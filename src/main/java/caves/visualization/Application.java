package caves.visualization;

import caves.generator.CaveSampleSpace;
import caves.generator.PathGenerator;
import caves.generator.mesh.MeshGenerator;
import caves.generator.util.Vector3;
import caves.visualization.window.ApplicationContext;
import caves.visualization.window.DeviceContext;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAllocLong;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public final class Application implements AutoCloseable {
    private static final int DEFAULT_WINDOW_WIDTH = 800;
    private static final int DEFAULT_WINDOW_HEIGHT = 600;
    private static final long UINT64_MAX = 0xFFFFFFFFFFFFFFFFL; // or "-1L", but this looks nicer.
    private static final int MAX_FRAMES_IN_FLIGHT = 2;

    private static final float DEGREES_PER_SECOND = 45.0f;

    private final ApplicationContext appContext;
    private final long[] imageAvailableSemaphores;
    private final long[] renderFinishedSemaphores;

    /** Fences for ensuring images that have been sent to GPU are presented before re-using them. */
    private final long[] inFlightFences;
    private final long[] imagesInFlight;

    /** Indicates that framebuffers have just resized and the swapchain should be re-created. */
    private boolean framebufferResized;

    /**
     * Configures a new visualization application. Call {@link #run()} to start the app.
     *
     * @param validation should validation/debug features be enabled.
     */
    public Application(final boolean validation) {
        final var caveLength = 40;
        final var spacing = 3f;
        final var cave = new PathGenerator().generate(new Vector3(0.0f, 0.0f, 0.0f),
                                                      caveLength,
                                                      spacing,
                                                      420);
        final float densityModifier = 1.0f;
        final var maxDensity = 1.0f;
        final var surfaceLevel = 1.0f;
        final var resolution = 0.5f;
        final var margin = 5.0f;
        final var sampleSpace = new CaveSampleSpace(cave,
                                                    margin,
                                                    resolution,
                                                    (path, pos) -> Math.min(maxDensity, path.distanceTo(pos) / densityModifier));

        final var pointVertices = new ArrayList<Vertex>(sampleSpace.getSize());
        for (var sampleIndex = 0; sampleIndex < sampleSpace.getSize(); ++sampleIndex) {
            final var density = sampleSpace.getDensity(sampleIndex);
            final var pos = sampleSpace.getPos(sampleIndex);

            if (density >= surfaceLevel) {
                continue;
            }

            final var color = 1.0f;
            pointVertices.add(new Vertex(new Vector3f(pos.getX(), pos.getY(), pos.getZ()),
                                         new Vector3f(color, color, color)));
        }

        final var pointIndices = IntStream.range(0, pointVertices.size())
                                          .boxed()
                                          .toArray(Integer[]::new);

        final var lineVertices = Arrays.stream(cave.getNodesOrdered())
                                       .map(pos -> new Vertex(new Vector3f(pos.getX(), pos.getY(), pos.getZ()),
                                                              new Vector3f(1.0f, 1.0f, 0.0f)))
                                       .toArray(Vertex[]::new);
        final var lineIndices = IntStream.range(0, lineVertices.length)
                                         .boxed()
                                         .toArray(Integer[]::new);

        final var meshGenerator = new MeshGenerator(sampleSpace);
        final var polygonVertices = new ArrayList<Vector3>();
        final var polygonIndices = new ArrayList<Integer>();
        meshGenerator.generateMeshForRegion(polygonVertices,
                                            polygonIndices,
                                            surfaceLevel,
                                            0, 0, 0,
                                            sampleSpace.getCountX(),
                                            sampleSpace.getCountY(),
                                            sampleSpace.getCountZ());

        this.appContext = new ApplicationContext(DEFAULT_WINDOW_WIDTH,
                                                 DEFAULT_WINDOW_HEIGHT,
                                                 validation,
                                                 pointVertices.toArray(Vertex[]::new),
                                                 pointIndices,
                                                 lineVertices,
                                                 lineIndices,
                                                 polygonVertices.stream()
                                                                .map(pos -> new Vertex(new Vector3f(pos.getX(), pos.getY(), pos.getZ()),
                                                                                       new Vector3f(0.5f, 0.5f, 0.5f)))
                                                                .toArray(Vertex[]::new),
                                                 polygonIndices.toArray(Integer[]::new));
        this.appContext.getWindow().onResize((windowHandle, width, height) -> this.framebufferResized = true);

        final var deviceContext = this.appContext.getDeviceContext();
        final var renderContext = this.appContext.getRenderContext();

        this.imageAvailableSemaphores = createSemaphores(MAX_FRAMES_IN_FLIGHT, deviceContext);
        this.renderFinishedSemaphores = createSemaphores(MAX_FRAMES_IN_FLIGHT, deviceContext);
        this.inFlightFences = createFences(MAX_FRAMES_IN_FLIGHT, deviceContext);
        this.imagesInFlight = new long[renderContext.getSwapChainImageCount()];
        Arrays.fill(this.imagesInFlight, VK_NULL_HANDLE);
    }

    private static long[] createFences(final int count, final DeviceContext deviceContext) {
        try (var stack = stackPush()) {
            return IntStream.range(0, count)
                            .mapToLong(i -> createFence(stack, deviceContext))
                            .toArray();
        }
    }

    private static long createFence(final MemoryStack stack, final DeviceContext deviceContext) {
        final var fenceInfo = VkFenceCreateInfo
                .callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                .flags(VK_FENCE_CREATE_SIGNALED_BIT); // Create as signaled to prevent issues during first frames

        final var pFence = memAllocLong(1);
        final var result = vkCreateFence(deviceContext.getDeviceHandle(), fenceInfo, null, pFence);
        if (result != VK_SUCCESS) {
            throw new IllegalStateException("Creating fence failed: "
                                                    + translateVulkanResult(result));
        }
        return pFence.get(0);
    }

    private static long[] createSemaphores(final int count, final DeviceContext deviceContext) {
        try (var stack = stackPush()) {
            return IntStream.range(0, count)
                            .mapToLong(i -> createSemaphore(stack, deviceContext))
                            .toArray();
        }
    }

    private static long createSemaphore(
            final MemoryStack stack,
            final DeviceContext deviceContext
    ) {
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
        return pSemaphore.get(0);
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
            this.appContext.getRenderContext().updateUniforms(imageIndex, angle);

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

        this.appContext.close();
    }
}
