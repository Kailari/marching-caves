package caves.visualization;

import caves.visualization.window.ApplicationContext;
import caves.visualization.window.DeviceContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.util.Arrays;
import java.util.stream.IntStream;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public final class Application {
    private static final int DEFAULT_WINDOW_WIDTH = 800;
    private static final int DEFAULT_WINDOW_HEIGHT = 600;
    private static final long UINT64_MAX = 0xFFFFFFFFFFFFFFFFL; // or "-1L", but this looks nicer.
    private static final int MAX_FRAMES_IN_FLIGHT = 2;

    private static final float DEGREES_PER_SECOND = 90.0f;

    private final boolean validation;

    /** Indicates that framebuffers have just resized and the swapchain should be re-created. */
    private boolean framebufferResized = false;

    /**
     * Configures a new visualization application. Call {@link #run()} to start the app.
     *
     * @param validation should validation/debug features be enabled.
     */
    public Application(final boolean validation) {
        this.validation = validation;
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
        final var error = vkCreateFence(deviceContext.getDevice(), fenceInfo, null, pFence);
        if (error != VK_SUCCESS) {
            throw new IllegalStateException("Creating fence failed: "
                                                    + translateVulkanResult(error));
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
        final var semaphoreInfo = VkSemaphoreCreateInfo
                .callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

        final var pSemaphore = memAllocLong(1);
        final var error = vkCreateSemaphore(deviceContext.getDevice(),
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
        try (var app = new ApplicationContext(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT, this.validation)) {
            final var deviceContext = app.getDeviceContext();
            final var renderContext = app.getRenderContext();
            final var graphicsQueue = deviceContext.getGraphicsQueue();
            final var presentQueue = deviceContext.getPresentQueue();

            final var imageAvailableSemaphores = createSemaphores(MAX_FRAMES_IN_FLIGHT, deviceContext);
            final var renderFinishedSemaphores = createSemaphores(MAX_FRAMES_IN_FLIGHT, deviceContext);
            final var inFlightFences = createFences(MAX_FRAMES_IN_FLIGHT, deviceContext);
            final var imagesInFlight = new long[renderContext.getSwapChainImageCount()];
            Arrays.fill(imagesInFlight, VK_NULL_HANDLE);

            this.framebufferResized = false;
            app.getWindow().onResize((windowHandle, width, height) -> this.framebufferResized = true);

            app.getWindow().show();
            var angle = 0.0f;
            var currentFrame = 0L;
            var lastFrameTime = System.currentTimeMillis();
            while (!app.getWindow().shouldClose()) {
                final var currentTime = System.currentTimeMillis();
                final var delta = (lastFrameTime - currentTime) / 1000.0;
                lastFrameTime = currentTime;

                glfwPollEvents();
                final var swapchain = renderContext.getSwapChain();

                final var imageAvailableSemaphore =
                        imageAvailableSemaphores[(int) (currentFrame % MAX_FRAMES_IN_FLIGHT)];
                final var renderFinishedSemaphore =
                        renderFinishedSemaphores[(int) (currentFrame % MAX_FRAMES_IN_FLIGHT)];
                final var inFlightFence =
                        inFlightFences[(int) (currentFrame % MAX_FRAMES_IN_FLIGHT)];

                vkWaitForFences(deviceContext.getDevice(), inFlightFence, true, UINT64_MAX);
                try (var stack = stackPush()) {
                    final var pImageIndex = stack.callocInt(1);
                    final var acquireResult = vkAcquireNextImageKHR(deviceContext.getDevice(),
                                                                    swapchain.getHandle(),
                                                                    UINT64_MAX,
                                                                    imageAvailableSemaphore,
                                                                    VK_NULL_HANDLE,
                                                                    pImageIndex);
                    if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
                        renderContext.notifyOutOfDateSwapchain();
                        continue;
                    } else if (acquireResult != VK_SUCCESS && acquireResult != VK_SUBOPTIMAL_KHR) {
                        throw new IllegalStateException("Acquiring swapchain image has failed!");
                    }
                    final var imageIndex = pImageIndex.get(0);

                    // AFTER we have acquired an image, wait until no-one else uses that image
                    if (imagesInFlight[imageIndex] != VK_NULL_HANDLE) {
                        vkWaitForFences(deviceContext.getDevice(), imagesInFlight[imageIndex], true, UINT64_MAX);
                    }
                    // Now we know that no-one else is using the image, thus we can safely claim it
                    // ourselves.
                    imagesInFlight[imageIndex] = inFlightFence;

                    // Update uniforms
                    angle += DEGREES_PER_SECOND * delta;
                    renderContext.updateUniforms(imageIndex, angle);

                    // Submit the render command buffers
                    final var signalSemaphores = stack.mallocLong(1);
                    signalSemaphores.put(renderFinishedSemaphore);
                    signalSemaphores.flip();

                    final var waitSemaphores = stack.mallocLong(1);
                    waitSemaphores.put(imageAvailableSemaphore);
                    waitSemaphores.flip();

                    final var waitStages = stack.mallocInt(1);
                    waitStages.put(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
                    waitStages.flip();

                    final var pCommandBuffer = memAllocPointer(1);
                    pCommandBuffer.put(renderContext.getCommandBufferForImage(imageIndex));
                    pCommandBuffer.flip();

                    final var submitInfo = VkSubmitInfo
                            .calloc()
                            .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                            .waitSemaphoreCount(1)
                            .pWaitSemaphores(waitSemaphores)
                            .pWaitDstStageMask(waitStages)
                            .pSignalSemaphores(signalSemaphores)
                            .pCommandBuffers(pCommandBuffer);

                    // We have claimed this fence, but have no clue in what state it is in. As we
                    // know by now that the fence has no other users anymore, we can just reset
                    // the darn thing to be sure its in valid state.
                    vkResetFences(deviceContext.getDevice(), inFlightFence);
                    final var error = vkQueueSubmit(graphicsQueue, submitInfo, inFlightFence);
                    if (error != VK_SUCCESS) {
                        throw new IllegalStateException("Submitting draw command buffer failed: "
                                                                + translateVulkanResult(error));
                    }
                    memFree(pCommandBuffer);
                    submitInfo.free();

                    final var swapChains = stack.mallocLong(1);
                    swapChains.put(swapchain.getHandle());
                    swapChains.flip();

                    final var presentInfo = VkPresentInfoKHR
                            .callocStack(stack)
                            .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                            .pWaitSemaphores(signalSemaphores)
                            .swapchainCount(1)
                            .pSwapchains(swapChains)
                            .pImageIndices(pImageIndex);

                    final var presentResult = vkQueuePresentKHR(presentQueue, presentInfo);
                    final var framebufferOutOfDateOrSuboptimal =
                            presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR;
                    if (framebufferOutOfDateOrSuboptimal || framebufferResized) {
                        renderContext.notifyOutOfDateSwapchain();
                        framebufferResized = false;
                    } else if (presentResult != VK_SUCCESS) {
                        throw new IllegalStateException("Presenting a swapchain image has failed!");
                    }
                }
                currentFrame++;
            }

            // Wait until everything has finished
            vkQueueWaitIdle(graphicsQueue);
            vkQueueWaitIdle(presentQueue);

            for (var i = 0; i < MAX_FRAMES_IN_FLIGHT; ++i) {
                vkDestroySemaphore(deviceContext.getDevice(), imageAvailableSemaphores[i], null);
                vkDestroySemaphore(deviceContext.getDevice(), renderFinishedSemaphores[i], null);
                vkDestroyFence(deviceContext.getDevice(), inFlightFences[i], null);
            }
            System.out.println("Finished.");
        }
    }

}
