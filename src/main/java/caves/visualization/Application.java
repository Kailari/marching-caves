package caves.visualization;

import caves.generator.CavePath;
import caves.generator.CaveSampleSpace;
import caves.generator.PathGenerator;
import caves.generator.mesh.MeshGenerator;
import caves.generator.util.Vector3;
import caves.visualization.rendering.command.CommandPool;
import caves.visualization.rendering.mesh.Mesh;
import caves.visualization.window.ApplicationContext;
import caves.visualization.window.DeviceContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

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

    private final Mesh<PolygonVertex> caveMesh;
    private final Mesh<LineVertex> lineMesh;
    private final Mesh<PointVertex> pointMesh;

    /** Indicates that framebuffers have just resized and the swapchain should be re-created. */
    private boolean framebufferResized;

    /**
     * Configures a new visualization application. Call {@link #run()} to start the app.
     *
     * @param validation should validation/debug features be enabled.
     */
    public Application(final boolean validation) {
        final var caveLength = 40;
        final var spacing = 10f;
        final var surfaceLevel = 0.5f;
        final var samplesPerUnit = 1.0f / 2;
        final var pathInfluenceRadius = 20.0;
        final var floorFlatness = 1.0;

        final var start = new Vector3(0.0f, 0.0f, 0.0f);
        final var startTime = System.nanoTime();
        final var cavePath = new PathGenerator().generate(start, caveLength, spacing, 420);

        final var margin = (float) pathInfluenceRadius + 1;
        final var densityFunction = createDensityFunction(pathInfluenceRadius, floorFlatness);
        final var sampleSpace = new CaveSampleSpace(cavePath, margin, samplesPerUnit, densityFunction);

        final var meshGenerator = new MeshGenerator(sampleSpace);
        final var caveVertices = new ArrayList<Vector3>();
        final var caveIndices = new ArrayList<Integer>();
        final var caveNormals = new ArrayList<Vector3>();
        final var startX = (int) Math.floor(Math.abs(start.getX() - sampleSpace.getMin().getX()) * samplesPerUnit);
        final var startY = (int) Math.floor(Math.abs(start.getY() - sampleSpace.getMin().getY()) * samplesPerUnit);
        final var startZ = (int) Math.floor(Math.abs(start.getZ() - sampleSpace.getMin().getZ()) * samplesPerUnit);
        meshGenerator.generate(caveVertices, caveNormals, caveIndices, surfaceLevel, startX, startY, startZ);

        final var timeElapsed = (System.nanoTime() - startTime) / 1_000_000_000.0;
        System.out.printf("Generation finished! (%.3fs)\n\n", timeElapsed);

        System.out.println("Initializing the visualization");
        this.appContext = new ApplicationContext(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT, validation);
        this.appContext.getWindow().onResize((windowHandle, width, height) -> this.framebufferResized = true);
        final var deviceContext = this.appContext.getDeviceContext();

        try (var commandPool = new CommandPool(deviceContext,
                                               deviceContext.getQueueFamilies().getTransfer())
        ) {
            System.out.println("\nConstructing meshes:");
            final var middle = cavePath.getAveragePosition();

            System.out.print("\t-> Constructing actual vertices from Marching Cubes vectors...");
            final var startTimeCave = System.nanoTime();
            this.caveMesh = Meshes.createCaveMesh(middle,
                                                  caveIndices,
                                                  caveVertices,
                                                  caveNormals,
                                                  deviceContext,
                                                  commandPool);
            System.out.printf(" Done! (%.3fs)\n",
                              (System.nanoTime() - startTimeCave) / 1_000_000_000.0);

            System.out.print("\t-> Creating additional path-line visualization...");
            final var startTimeLines = System.nanoTime();
            this.lineMesh = Meshes.createLineMesh(cavePath,
                                                  middle,
                                                  deviceContext,
                                                  commandPool);
            System.out.printf(" Done! (%.3fs)\n",
                              (System.nanoTime() - startTimeLines) / 1_000_000_000.0);

            System.out.print("\t-> Creating additional point-cloud visualization...");
            final var startTimePoints = System.nanoTime();
            this.pointMesh = Meshes.createPointMesh(surfaceLevel,
                                                    sampleSpace,
                                                    startX,
                                                    startY,
                                                    startZ,
                                                    middle,
                                                    deviceContext,
                                                    commandPool);
            System.out.printf(" Done! (%.3fs)\n",
                              (System.nanoTime() - startTimePoints) / 1_000_000_000.0);

            this.appContext.setMeshes(this.caveMesh, this.lineMesh, this.pointMesh);
        }

        final var renderContext = this.appContext.getRenderContext();

        this.imageAvailableSemaphores = createSemaphores(MAX_FRAMES_IN_FLIGHT, deviceContext);
        this.renderFinishedSemaphores = createSemaphores(MAX_FRAMES_IN_FLIGHT, deviceContext);
        this.inFlightFences = createFences(MAX_FRAMES_IN_FLIGHT, deviceContext);
        this.imagesInFlight = new long[renderContext.getSwapChainImageCount()];
        Arrays.fill(this.imagesInFlight, VK_NULL_HANDLE);

        this.lookAtDistance = Math.max(sampleSpace.getMin().length(),
                                       sampleSpace.getMax().length()) + margin;

        System.out.printf("Everything done! (total %.3fs)\n\n", (System.nanoTime() - startTime) / 1_000_000_000.0);
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

    private static double densityCurve(final double t) {
        final var a = 0.0;
        final var b = 1.0;
        return lerp(a, b, t);
    }

    private static double lerp(final double a, final double b, final double t) {
        return (1 - t) * a + t * b;
    }

    private BiFunction<CavePath, Vector3, Float> createDensityFunction(
            final double pathInfluenceRadius,
            final double floorFlatness
    ) {

        return (path, pos) -> {
            final var closestPoint = path.closestPoint(pos);

            final var distance = Math.sqrt(closestPoint.distanceSq(pos));
            final var clampedDistanceAlpha = Math.min(1.0, distance / pathInfluenceRadius);
            final var baseDensity = Math.min(1.0, densityCurve(clampedDistanceAlpha));

            final var up = new Vector3(0.0f, 1.0f, 0.0f);
            final var direction = closestPoint.sub(pos, new Vector3())
                                              .normalize();

            // Dot product can kind of be thought as to signify "how perpendicular two vectors are?"
            // or "what is the size of the portion of these two vectors that overlaps?". Here, we
            // are working with up axis and a direction, thus taking their dot product in this
            // context practically means "how upwards the direction vector points".
            //
            // Both are unit vectors so resulting scalar has maximum absolute value of 1.0.
            //
            // Furthermore, for the ceiling the dot product is negative, so by clamping to zero we
            // get a nice weight multiplier for the floor. (The resulting value is zero for walls
            // and the ceiling).
            //
            // From there, just lerp between the base density and higher density (based on the base
            // density) to get a nice flat floor.
            final var floorWeight = Math.max(0.0, direction.dot(up));
            final var floorDensity = Math.min(1.0, (Math.pow(distance, 1 + floorFlatness)) / pathInfluenceRadius);
            return (float) lerp(baseDensity, floorDensity, floorWeight);
        };
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

        this.caveMesh.close();
        this.lineMesh.close();
        this.pointMesh.close();

        this.appContext.close();
    }

}
