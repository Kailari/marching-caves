package caves;

import caves.window.DeviceContext;
import caves.window.Window;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.IntStream;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public final class Application {
    private static final boolean VALIDATION = Boolean.parseBoolean(System.getProperty("vulkan.validation", "true"));
    private static final int DEFAULT_WINDOW_WIDTH = 800;
    private static final int DEFAULT_WINDOW_HEIGHT = 600;
    private static final long UINT64_MAX = 0xFFFFFFFFFFFFFFFFL; // or "-1L", but this is neat.
    private static final int MAX_FRAMES_IN_FLIGHT = 2;

    private final ByteBuffer[] validationLayers;

    private Application(final ByteBuffer[] validationLayers) {
        this.validationLayers = validationLayers;
    }

    /**
     * Application main entry-point.
     *
     * @param args un-parsed command-line arguments
     */
    public static void main(final String[] args) {
        System.out.println("Validation: " + VALIDATION);
        final var validationLayers = VALIDATION
                ? new ByteBuffer[]
                {
                        memUTF8("VK_LAYER_LUNARG_standard_validation"),
                }
                : new ByteBuffer[0];

        final var app = new Application(validationLayers);
        app.run();
    }

    private static VkQueue getGraphicsQueue(final DeviceContext deviceContext) {
        return getQueue(deviceContext.getDevice(), deviceContext.getGraphicsQueueFamilyIndex());
    }

    private static VkQueue getPresentationQueue(final DeviceContext deviceContext) {
        return getQueue(deviceContext.getDevice(), deviceContext.getPresentationQueueFamilyIndex());
    }

    private static VkQueue getQueue(final VkDevice device, final int queueFamilyIndex) {
        try (var stack = stackPush()) {
            final var pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device, queueFamilyIndex, 0, pQueue);
            return new VkQueue(pQueue.get(0), device);
        }
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
                .flags(VK_FENCE_CREATE_SIGNALED_BIT); // Create in signaled state. This prevents issues on first frames

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

    private void run() {
        try (var window = new Window(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT, validationLayers)) {
            final var deviceContext = window.getDeviceContext();
            final var renderContext = window.getRenderContext();
            final var graphicsQueue = getGraphicsQueue(deviceContext);
            final var presentQueue = getPresentationQueue(deviceContext);

            final var imageAvailableSemaphores = createSemaphores(MAX_FRAMES_IN_FLIGHT, deviceContext);
            final var renderFinishedSemaphores = createSemaphores(MAX_FRAMES_IN_FLIGHT, deviceContext);
            final var inFlightFences = createFences(MAX_FRAMES_IN_FLIGHT, deviceContext);
            final var imagesInFlight = new long[renderContext.getSwapChainImageCount()];
            Arrays.fill(imagesInFlight, VK_NULL_HANDLE);

            window.show();
            var currentFrame = 0L;
            while (!window.shouldClose()) {
                final var windowWidth = DEFAULT_WINDOW_WIDTH;
                final var windowHeight = DEFAULT_WINDOW_HEIGHT;
                glfwPollEvents();
                final var swapchain = renderContext.getSwapChain(windowWidth, windowHeight);

                final var imageAvailableSemaphore =
                        imageAvailableSemaphores[(int) (currentFrame % MAX_FRAMES_IN_FLIGHT)];
                final var renderFinishedSemaphore =
                        renderFinishedSemaphores[(int) (currentFrame % MAX_FRAMES_IN_FLIGHT)];
                final var inFlightFence =
                        inFlightFences[(int) (currentFrame % MAX_FRAMES_IN_FLIGHT)];

                vkWaitForFences(deviceContext.getDevice(), inFlightFence, true, UINT64_MAX);
                try (var stack = stackPush()) {
                    final var pImageIndex = stack.callocInt(1);
                    vkAcquireNextImageKHR(deviceContext.getDevice(),
                                          swapchain.getHandle(),
                                          UINT64_MAX,
                                          imageAvailableSemaphore,
                                          VK_NULL_HANDLE,
                                          pImageIndex);
                    final var imageIndex = pImageIndex.get(0);

                    // AFTER we have acquired an image, wait until no-one else uses that image
                    if (imagesInFlight[imageIndex] != VK_NULL_HANDLE) {
                        vkWaitForFences(deviceContext.getDevice(), imagesInFlight[imageIndex], true, UINT64_MAX);
                    }
                    // Now we know that no-one else is using the image, thus we can safely claim it
                    // ourselves.
                    imagesInFlight[imageIndex] = inFlightFence;

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

                    vkQueuePresentKHR(presentQueue, presentInfo);
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
