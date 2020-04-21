package caves.visualization;

import caves.generator.ChunkCaveSampleSpace;
import caves.generator.PathGenerator;
import caves.generator.density.EdgeDensityFunction;
import caves.generator.density.PathDensityFunction;
import caves.generator.mesh.MeshGenerator;
import caves.util.collections.GrowingAddOnlyList;
import caves.util.collections.LongMap;
import caves.util.math.Vector3;
import caves.visualization.rendering.command.CommandPool;
import caves.visualization.rendering.mesh.Mesh;
import caves.visualization.window.ApplicationContext;
import caves.visualization.window.DeviceContext;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Optional;

import static caves.generator.ChunkCaveSampleSpace.CHUNK_SIZE;
import static caves.util.math.MathUtil.fastFloor;
import static caves.util.profiler.Profiler.PROFILER;
import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAllocLong;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

@SuppressWarnings("SameParameterValue")
public final class Application implements AutoCloseable {
    private static final int DEFAULT_WINDOW_WIDTH = 800;
    private static final int DEFAULT_WINDOW_HEIGHT = 600;
    private static final long UINT64_MAX = 0xFFFFFFFFFFFFFFFFL; // or "-1L", but this looks nicer.
    private static final int MAX_FRAMES_IN_FLIGHT = 2;

    private static final float DEGREES_PER_SECOND = 360.0f / 10.0f;

    private final ApplicationContext appContext;
    private final long[] imageAvailableSemaphores;
    private final long[] renderFinishedSemaphores;

    /** Fences for ensuring images that have been sent to GPU are presented before re-using them. */
    private final long[] inFlightFences;
    private final long[] imagesInFlight;
    private final float lookAtDistance;

    @Nullable private Collection<Mesh<PolygonVertex>> caveMeshes;
    @Nullable private Mesh<LineVertex> lineMesh;

    /** Indicates that framebuffers have just resized and the swapchain should be re-created. */
    private boolean framebufferResized;

    /**
     * Configures a new visualization application. Call {@link #run()} to start the app.
     *
     * @param validation should validation/debug features be enabled.
     */
    public Application(final boolean validation) {
        final var caveLength = 16000;
        final var spacing = 10f;

        final var surfaceLevel = 0.85f;
        final var samplesPerUnit = 0.25f;

        final var floorFlatness = 0.65;
        final var caveRadius = 40.0;
        final var maxInfluenceRadius = caveRadius + 20;

        final var linesVisible = true;

        final var start = new Vector3(0.0f, 0.0f, 0.0f);
        PROFILER.start("Initialization");

        PROFILER.start("Generating path");
        final var cavePath = new PathGenerator().generate(start,
                                                          caveLength,
                                                          spacing,
                                                          (float) maxInfluenceRadius,
                                                          420);
        PROFILER.end();

        PROFILER.start("Initializing the visualization");
        this.appContext = new ApplicationContext(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT, validation);
        this.appContext.getWindow()
                       .onResize((windowHandle, width, height) -> this.framebufferResized = true);
        final var deviceContext = this.appContext.getDeviceContext();
        PROFILER.end();

        PROFILER.start("Constructing the cave");

        PROFILER.start("Creating isosurface mesh with Marching Cubes");
        final var edgeFunc = new EdgeDensityFunction(maxInfluenceRadius,
                                                     caveRadius,
                                                     floorFlatness);

        final var sampleSpace = new ChunkCaveSampleSpace(
                samplesPerUnit,
                new PathDensityFunction(cavePath,
                                        caveRadius,
                                        maxInfluenceRadius,
                                        edgeFunc));

        try (var commandPool = new CommandPool(deviceContext,
                                               deviceContext.getQueueFamilies().getTransfer())
        ) {
            final var middle = cavePath.getAveragePosition();

            final var meshGenerator = new MeshGenerator(sampleSpace);
            final var chunkMeshes = new LongMap<Mesh<PolygonVertex>>(1024);

            meshGenerator.generate(cavePath, surfaceLevel, (x, y, z, chunk) -> {
                final var chunkX = fastFloor(x / (float) CHUNK_SIZE);
                final var chunkY = fastFloor(y / (float) CHUNK_SIZE);
                final var chunkZ = fastFloor(z / (float) CHUNK_SIZE);
                final var index = ChunkCaveSampleSpace.chunkIndex(chunkX, chunkY, chunkZ);

                final var vertices = chunk.getVertices();
                final var normals = chunk.getNormals();
                final var indices = chunk.getIndices();

                if (vertices == null || indices == null || normals == null) {
                    return;
                }

                final var existing = chunkMeshes.get(index);
                if (existing != null) {
                    existing.close();
                }

                chunkMeshes.put(index, Meshes.createChunkMesh(middle,
                                                              deviceContext,
                                                              commandPool,
                                                              chunk.getVertices(),
                                                              chunk.getNormals(),
                                                              chunk.getIndices()));
            });
            this.caveMeshes = new GrowingAddOnlyList<>(chunkMeshes.getSize());
            chunkMeshes.values().forEach(this.caveMeshes::add);

            PROFILER.next("Creating path visualization (line mesh)");
            this.lineMesh = linesVisible
                    ? Meshes.createLineMesh(cavePath,
                                            middle,
                                            deviceContext,
                                            commandPool)
                    : null;

            PROFILER.end();

            logGPUMemoryProfilingInfo(deviceContext);
        }
        this.appContext.setMeshes(this.caveMeshes, this.lineMesh);

        PROFILER.log("-> Generated {} vertices.", sampleSpace.getTotalVertices());
        PROFILER.log("-> Sample space is split into {} chunks.", sampleSpace.getChunkCount());
        PROFILER.log("-> (Chunk size is {}x{}x{} = {} samples).",
                     CHUNK_SIZE, CHUNK_SIZE, CHUNK_SIZE,
                     CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE);

        PROFILER.end();


        final var renderContext = this.appContext.getRenderContext();

        this.imageAvailableSemaphores = createSemaphores(MAX_FRAMES_IN_FLIGHT, deviceContext);
        this.renderFinishedSemaphores = createSemaphores(MAX_FRAMES_IN_FLIGHT, deviceContext);
        this.inFlightFences = createFences(MAX_FRAMES_IN_FLIGHT, deviceContext);
        this.imagesInFlight = new long[renderContext.getSwapChainImageCount()];

        //noinspection ExplicitArrayFilling
        for (var i = 0; i < this.imagesInFlight.length; ++i) {
            this.imagesInFlight[i] = VK_NULL_HANDLE;
        }

        this.lookAtDistance = Math.max(sampleSpace.getMin().length(),
                                       sampleSpace.getMax().length()) + (float) caveRadius + 1;

        PROFILER.end();
    }

    private static long[] createFences(final int count, final DeviceContext deviceContext) {
        final var fences = new long[count];
        try (var stack = stackPush()) {
            for (var i = 0; i < count; ++i) {
                // NOTE: Create as signaled to prevent starvation during first frames (flags)
                final var fenceInfo = VkFenceCreateInfo
                        .callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                        .flags(VK_FENCE_CREATE_SIGNALED_BIT);

                final var pFence = memAllocLong(1);
                final var result = vkCreateFence(deviceContext.getDeviceHandle(), fenceInfo, null, pFence);
                if (result != VK_SUCCESS) {
                    throw new IllegalStateException("Creating fence failed: "
                                                            + translateVulkanResult(result));
                }

                fences[i] = pFence.get(0);
            }
        }

        return fences;
    }

    private static long[] createSemaphores(final int count, final DeviceContext deviceContext) {
        final var semaphores = new long[count];
        try (var stack = stackPush()) {
            for (var i = 0; i < count; ++i) {
                final var semaphoreInfo = VkSemaphoreCreateInfo.callocStack(stack)
                                                               .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

                final var pSemaphore = memAllocLong(1);
                final var error = vkCreateSemaphore(deviceContext.getDeviceHandle(),
                                                    semaphoreInfo,
                                                    null,
                                                    pSemaphore);
                if (error != VK_SUCCESS) {
                    throw new IllegalStateException("Creating semaphore failed: "
                                                            + translateVulkanResult(error));
                }

                semaphores[i] = pSemaphore.get(0);
            }
        }

        return semaphores;
    }

    private void logGPUMemoryProfilingInfo(final DeviceContext deviceContext) {
        PROFILER.start("Gathering GPU memory profiling info");

        final var usageTotal = deviceContext.getMemoryAllocator().getUsageTotal();
        final var allocationTotal = deviceContext.getMemoryAllocator().getAllocationTotal();

        PROFILER.log("-> {} GPU memory allocations have been made.",
                     deviceContext.getMemoryAllocator().getAllocationCount());
        PROFILER.log("-> Total {} bytes of GPU memory is allocated (~{} kb)",
                     allocationTotal, Math.round(allocationTotal / 1000.0));
        PROFILER.log("-> {} bytes of allocated memory is in use (~{} kb)",
                     usageTotal, Math.round(usageTotal / 1000.0));
        PROFILER.log("-> ~{} kb of memory is wasted ({}% utilization)",
                     Math.round((allocationTotal - usageTotal) / 1000.0),
                     String.format("%.2f", (usageTotal / (double) allocationTotal) * 100.0));
        PROFILER.log("-> Size of the largest allocation is {} bytes (~{} kb)",
                     deviceContext.getMemoryAllocator().getLargestAllocationSize(),
                     Math.round(deviceContext.getMemoryAllocator().getAverageAllocationSize() / 1000.0));
        PROFILER.log("-> Average allocation is {} bytes (~{} kb)",
                     deviceContext.getMemoryAllocator().getAverageAllocationSize(),
                     Math.round(deviceContext.getMemoryAllocator().getAverageAllocationSize() / 1000.0));
        PROFILER.end();
    }

    /**
     * Runs the application. This method blocks until execution has finished.
     */
    public void run() {
        this.appContext.getWindow().show();
        var angle = 0.0f;
        var currentFrame = 0L;
        var lastFrameTime = System.currentTimeMillis();
        this.framebufferResized = false;
        while (!this.appContext.getWindow().shouldClose()) {
            final var currentTime = System.currentTimeMillis();
            final var delta = (lastFrameTime - currentTime) / 1000.0;
            lastFrameTime = currentTime;

            glfwPollEvents();
            this.appContext.getRenderContext().updateSwapChain();

            final var frameIndex = (int) (currentFrame % MAX_FRAMES_IN_FLIGHT);
            final var maybeImgIndex = tryAcquireNextImage(frameIndex);
            if (maybeImgIndex.isEmpty()) {
                // Swapchain was recreated, restart the frame. Note that frame index is NOT incremented.
                continue;
            }
            final int imageIndex = maybeImgIndex.get();

            // Update uniforms
            angle += DEGREES_PER_SECOND * delta;
            this.appContext.getRenderContext().updateUniforms(imageIndex, angle, this.lookAtDistance);

            // Submit and present
            submitCommandBuffer(imageIndex, frameIndex);
            presentFrame(imageIndex, frameIndex);
            currentFrame++;
        }
    }

    private Optional<Integer> tryAcquireNextImage(final int frameIndex) {
        final var inFlightFence = this.inFlightFences[frameIndex];
        final var imgAvailableSemaphore = this.imageAvailableSemaphores[frameIndex];

        final var device = this.appContext.getDeviceContext().getDeviceHandle();
        vkWaitForFences(device, inFlightFence, true, UINT64_MAX);

        final int imageIndex;
        try (var stack = stackPush()) {
            final var pImageIndex = stack.callocInt(1);
            final var acquireResult = vkAcquireNextImageKHR(device,
                                                            this.appContext.getRenderContext()
                                                                           .getSwapChain()
                                                                           .getHandle(),
                                                            UINT64_MAX,
                                                            imgAvailableSemaphore,
                                                            VK_NULL_HANDLE,
                                                            pImageIndex);
            if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
                this.appContext.getRenderContext()
                               .notifyOutOfDateSwapchain();
                return Optional.empty();
            } else if (acquireResult != VK_SUCCESS && acquireResult != VK_SUBOPTIMAL_KHR) {
                throw new IllegalStateException("Acquiring swapchain image has failed!");
            }
            imageIndex = pImageIndex.get(0);
        }

        // AFTER we have acquired an image, wait until no-one else uses that image
        if (this.imagesInFlight[imageIndex] != VK_NULL_HANDLE) {
            vkWaitForFences(device, this.imagesInFlight[imageIndex], true, UINT64_MAX);
        }
        // Now we know that no-one else is using the image, thus we can safely claim it
        // ourselves.
        this.imagesInFlight[imageIndex] = inFlightFence;

        return Optional.of(imageIndex);
    }

    private void submitCommandBuffer(final int imageIndex, final int frameIndex) {
        try (var stack = stackPush()) {
            final var submitInfo = VkSubmitInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(stack.longs(this.imageAvailableSemaphores[frameIndex]))
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                    .pSignalSemaphores(stack.longs(this.renderFinishedSemaphores[frameIndex]))
                    .pCommandBuffers(stack.pointers(this.appContext.getRenderContext()
                                                                   .getCommandBufferForImage(imageIndex)));

            final var inFlightFence = this.inFlightFences[frameIndex];
            final var device = this.appContext.getDeviceContext().getDeviceHandle();
            vkResetFences(device, inFlightFence);

            final var queue = this.appContext.getDeviceContext().getGraphicsQueue();
            final var result = vkQueueSubmit(queue, submitInfo, inFlightFence);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Submitting draw command buffer failed: "
                                                        + translateVulkanResult(result));
            }
        }
    }

    private void presentFrame(final int imageIndex, final int frameIndex) {
        try (var stack = stackPush()) {
            final var presentInfo = VkPresentInfoKHR
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                    .pWaitSemaphores(stack.longs(this.renderFinishedSemaphores[frameIndex]))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(this.appContext.getRenderContext().getSwapChain().getHandle()))
                    .pImageIndices(stack.ints(imageIndex));

            final var result = vkQueuePresentKHR(this.appContext.getDeviceContext().getPresentQueue(), presentInfo);
            final var framebufferOODOrSuboptimal = result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR;
            if (framebufferOODOrSuboptimal || this.framebufferResized) {
                this.appContext.getRenderContext().notifyOutOfDateSwapchain();
                this.framebufferResized = false;
            } else if (result != VK_SUCCESS) {
                throw new IllegalStateException("Presenting a swapchain image has failed: "
                                                        + translateVulkanResult(result));
            }
        }
    }

    @Override
    public void close() {
        // Wait until everything has finished
        final var deviceContext = this.appContext.getDeviceContext();
        vkQueueWaitIdle(deviceContext.getGraphicsQueue());
        vkQueueWaitIdle(deviceContext.getPresentQueue());

        final var device = deviceContext.getDeviceHandle();
        for (var i = 0; i < MAX_FRAMES_IN_FLIGHT; ++i) {
            vkDestroySemaphore(device, this.imageAvailableSemaphores[i], null);
            vkDestroySemaphore(device, this.renderFinishedSemaphores[i], null);
            vkDestroyFence(device, this.inFlightFences[i], null);
        }

        if (this.caveMeshes != null) {
            for (final var mesh : this.caveMeshes) {
                mesh.close();
            }
        }
        if (this.lineMesh != null) {
            this.lineMesh.close();
        }

        this.appContext.close();
    }

}
