package caves.visualization.rendering.renderpass;

import caves.visualization.rendering.swapchain.SwapChain;
import caves.visualization.window.DeviceContext;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

final class Framebuffers implements AutoCloseable {
    private final DeviceContext deviceContext;
    private final long[] swapChainFramebuffers;
    private final DepthBuffer depthBuffer;

    /**
     * Creates framebuffers for the swapchain. Each swapchain image gets its own attached
     * framebuffer.
     *
     * @param deviceContext    device context to use
     * @param renderPass       which render pass these framebuffers are used with
     * @param swapChain        the swapchain to use
     * @param depthImageFormat format for the depth buffer image
     */
    Framebuffers(
            final DeviceContext deviceContext,
            final long renderPass,
            final SwapChain swapChain,
            final int depthImageFormat
    ) {
        this.deviceContext = deviceContext;

        final var swapChainImageViews = swapChain.getImageViews();
        final var imageCount = swapChainImageViews.length;
        this.swapChainFramebuffers = new long[imageCount];

        this.depthBuffer = new DepthBuffer(this.deviceContext, swapChain, depthImageFormat);

        try (var stack = stackPush()) {
            final var extent = swapChain.getExtent();
            final var pFramebuffer = stack.mallocLong(1);
            for (var i = 0; i < imageCount; ++i) {
                final var pAttachments = stack.longs(swapChainImageViews[i],
                                                     this.depthBuffer.getImageView());
                final var createInfo = VkFramebufferCreateInfo
                        .callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                        .renderPass(renderPass)
                        .pAttachments(pAttachments)
                        .width(extent.width())
                        .height(extent.height())
                        .layers(1);

                final var error = vkCreateFramebuffer(this.deviceContext.getDeviceHandle(),
                                                      createInfo,
                                                      null,
                                                      pFramebuffer);
                if (error != VK_SUCCESS) {
                    throw new IllegalStateException("Failed to create framebuffer: "
                                                            + translateVulkanResult(error));
                }
                this.swapChainFramebuffers[i] = pFramebuffer.get(0);
            }
        }
    }

    /**
     * Gets the swapchain image framebuffers. Buffer with index <code>i</code> corresponds directly
     * to swapchain image view with index <code>i</code>
     *
     * @param imageIndex image index of the desired framebuffer
     *
     * @return handle for the framebuffer with the given image index
     */
    long get(final int imageIndex) {
        return this.swapChainFramebuffers[imageIndex];
    }

    /**
     * Releases the framebuffers in preparations for re-create or shutdown.
     */
    @Override
    public void close() {
        for (final var framebuffer : this.swapChainFramebuffers) {
            vkDestroyFramebuffer(this.deviceContext.getDeviceHandle(), framebuffer, null);
        }
        this.depthBuffer.close();
    }
}
