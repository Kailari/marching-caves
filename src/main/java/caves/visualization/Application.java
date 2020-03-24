package caves.visualization;

import caves.generator.CavePath;
import caves.generator.CaveSampleSpace;
import caves.generator.PathGenerator;
import caves.generator.mesh.MarchingCubesTables;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
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
        final var cave = new PathGenerator().generate(start, caveLength, spacing, 420);

        final var margin = (float) pathInfluenceRadius + 1;
        final var densityFunction = createDensityFunction(pathInfluenceRadius, floorFlatness);
        final var sampleSpace = new CaveSampleSpace(cave, margin, samplesPerUnit, densityFunction);

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

        System.out.println("Constructing meshes:");
        final var middle = Arrays.stream(cave.getNodesOrdered())
                                 .reduce((a, b) -> a.add(b, new Vector3()))
                                 .map(sum -> sum.mul(1.0f / caveLength, sum))
                                 .orElseThrow();

        System.out.print("\t-> Creating additional point-cloud visualization...");
        final var startTimePoints = System.nanoTime();
        final PointVertex[] pointVertices = findPointMeshVertices(surfaceLevel,
                                                                  sampleSpace,
                                                                  startX,
                                                                  startY,
                                                                  startZ,
                                                                  middle);
        final var pointIndices = IntStream.range(0, pointVertices.length)
                                          .boxed()
                                          .toArray(Integer[]::new);
        final var timeElapsedPoints = (System.nanoTime() - startTimePoints) / 1_000_000_000.0;
        System.out.printf(" Done! (%.3fs)\n", timeElapsedPoints);

        System.out.print("\t-> Creating additional path-line visualization...");
        final var startTimeLines = System.nanoTime();
        final var lineVertices = Arrays.stream(cave.getNodesOrdered())
                                       .map(pos -> new LineVertex(new Vector3f(pos.getX() - middle.getX(),
                                                                               pos.getY() - middle.getY(),
                                                                               pos.getZ() - middle.getZ())))
                                       .toArray(LineVertex[]::new);
        final var lineIndices = IntStream.range(0, lineVertices.length)
                                         .boxed()
                                         .toArray(Integer[]::new);
        final var timeElapsedLines = (System.nanoTime() - startTimeLines) / 1_000_000_000.0;
        System.out.printf(" Done! (%.3fs)\n", timeElapsedLines);

        final var polygonVertices = new PolygonVertex[caveVertices.size()];
        for (var i = 0; i < polygonVertices.length; ++i) {
            final var pos = caveVertices.get(i);
            final var normal = caveNormals.get(i);
            final var color = new Vector3f(0.7f, 0.3f, 0.1f);
            polygonVertices[i] = new PolygonVertex(new Vector3f(pos.getX() - middle.getX(),
                                                                pos.getY() - middle.getY(),
                                                                pos.getZ() - middle.getZ()),
                                                   new Vector3f(normal.getX(), normal.getY(), normal.getZ()),
                                                   color);
        }

        System.out.printf("Everything done! (total %.3fs)\n\n", (System.nanoTime() - startTime) / 1_000_000_000.0);

        this.lookAtDistance = Math.max(sampleSpace.getMin().length(),
                                       sampleSpace.getMax().length()) + margin;

        System.out.println("Starting the visualization");
        this.appContext = new ApplicationContext(
                DEFAULT_WINDOW_WIDTH,
                DEFAULT_WINDOW_HEIGHT,
                validation,
                pointVertices,
                pointIndices,
                lineVertices,
                lineIndices,
                polygonVertices,
                caveIndices.toArray(Integer[]::new));
        this.appContext.getWindow().onResize((windowHandle, width, height) -> this.framebufferResized = true);

        final var deviceContext = this.appContext.getDeviceContext();
        final var renderContext = this.appContext.getRenderContext();

        this.imageAvailableSemaphores = createSemaphores(MAX_FRAMES_IN_FLIGHT, deviceContext);
        this.renderFinishedSemaphores = createSemaphores(MAX_FRAMES_IN_FLIGHT, deviceContext);
        this.inFlightFences = createFences(MAX_FRAMES_IN_FLIGHT, deviceContext);
        this.imagesInFlight = new long[renderContext.getSwapChainImageCount()];
        Arrays.fill(this.imagesInFlight, VK_NULL_HANDLE);
    }

    private static PointVertex[] findPointMeshVertices(
            final float surfaceLevel,
            final CaveSampleSpace sampleSpace,
            final int startX,
            final int startY,
            final int startZ,
            final Vector3 middle
    ) {
        final var pointVertices = new ArrayList<PointVertex>();

        final var pointQueue = new ArrayDeque<PointVertexEntry>();
        final var alreadyQueued = new boolean[sampleSpace.getTotalCount()];
        pointQueue.add(new PointVertexEntry(startX, startY, startZ));

        while (!pointQueue.isEmpty()) {
            final var entry = pointQueue.pop();
            final var pos = sampleSpace.getPos(entry.x, entry.y, entry.z);
            final float density = sampleSpace.getDensity(entry.x, entry.y, entry.z);
            pointVertices.add(new PointVertex(new Vector3f(pos.getX() - middle.getX(),
                                                           pos.getY() - middle.getY(),
                                                           pos.getZ() - middle.getZ()),
                                              density));

            for (final var facing : MarchingCubesTables.Facing.values()) {
                final var x = entry.x + facing.getX();
                final var y = entry.y + facing.getY();
                final var z = entry.z + facing.getZ();
                final var index = sampleSpace.getSampleIndex(x, y, z);

                final var xOOB = x < 2 || x >= sampleSpace.getCountX() - 2;
                final var yOOB = y < 2 || y >= sampleSpace.getCountY() - 2;
                final var zOOB = z < 2 || z >= sampleSpace.getCountZ() - 2;
                final var isSolid = sampleSpace.getDensity(index, x, y, z) >= surfaceLevel;
                if (isSolid || xOOB || yOOB || zOOB || alreadyQueued[index]) {
                    continue;
                }
                pointQueue.add(new PointVertexEntry(x, y, z));
                alreadyQueued[index] = true;
            }
        }

        return pointVertices.toArray(PointVertex[]::new);
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

        this.appContext.close();
    }

    private static final class PointVertexEntry {
        private final int x;
        private final int y;
        private final int z;

        private PointVertexEntry(final int x, final int y, final int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
