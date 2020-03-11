package caves;

import caves.window.DeviceContext;
import caves.window.Window;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;

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

    private void run() {
        try (var window = new Window(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT, validationLayers)) {
            final var deviceContext = window.getDeviceContext();
            final var renderContext = window.getRenderContext();
            final var graphicsQueue = getGraphicsQueue(deviceContext);
            final var presentationQueue = getPresentationQueue(deviceContext);

            final var imageAvailableSemaphore = createSemaphore(deviceContext);
            final var renderFinishedSemaphore = createSemaphore(deviceContext);

            window.show();
            while (!window.shouldClose()) {
                final var windowWidth = DEFAULT_WINDOW_WIDTH;
                final var windowHeight = DEFAULT_WINDOW_HEIGHT;
                glfwPollEvents();
                final var swapchain = renderContext.getSwapChain(windowWidth, windowHeight);

                try (var stack = stackPush()) {
                    final var pImageIndex = stack.callocInt(1);
                    vkAcquireNextImageKHR(deviceContext.getDevice(),
                                          swapchain.getHandle(),
                                          Long.MAX_VALUE,
                                          imageAvailableSemaphore,
                                          VK_NULL_HANDLE,
                                          pImageIndex);
                    final var imageIndex = pImageIndex.get(0);

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
                            .pSignalSemaphores(signalSemaphores)
                            .pWaitDstStageMask(waitStages)
                            .pCommandBuffers(pCommandBuffer);

                    final var error = vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE);
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

                    vkQueuePresentKHR(presentationQueue, presentInfo);
                }
            }

            vkDestroySemaphore(deviceContext.getDevice(), imageAvailableSemaphore, null);
            vkDestroySemaphore(deviceContext.getDevice(), renderFinishedSemaphore, null);
            System.out.println("Finished.");
        }
    }

    private long createSemaphore(final DeviceContext deviceContext) {
        final var semaphoreInfo = VkSemaphoreCreateInfo
                .calloc()
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
}
