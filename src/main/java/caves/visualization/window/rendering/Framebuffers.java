package caves.visualization.window.rendering;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class Framebuffers implements AutoCloseable {
    private final VkDevice device;

    private long[] swapChainFramebuffers;
    private boolean cleanedUp;

    /**
     * Gets the swapchain image framebuffers. Buffer with index <code>i</code> corresponds directly
     * to swapchain image view with index <code>i</code>
     *
     * @return the framebuffers
     */
    public long[] getSwapChainFramebuffers() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried to fetch framebuffers before re-creating!");
        }

        return this.swapChainFramebuffers;
    }

    /**
     * Creates framebuffers for the swapchain. Each swapchain image gets its own attached
     * framebuffer.
     *
     * @param device           logical device the swapchain is created on
     * @param graphicsPipeline graphics pipeline to use
     * @param swapChain        the swapchain to use
     */
    public Framebuffers(
            final VkDevice device,
            final GraphicsPipeline graphicsPipeline,
            final SwapChain swapChain
    ) {
        this.device = device;
        this.cleanedUp = true;

        this.recreate(graphicsPipeline, swapChain);
    }

    /**
     * Re-creates the framebuffers for all swapchain image views.
     *
     * @param graphicsPipeline graphics pipeline to use
     * @param swapChain        the swapchain to use
     */
    public void recreate(
            final GraphicsPipeline graphicsPipeline,
            final SwapChain swapChain
    ) {
        if (!this.cleanedUp) {
            throw new IllegalStateException("Tried to re-create framebuffers without cleaning up!");
        }

        final var swapChainImageViews = swapChain.getImageViews();
        final var imageCount = swapChainImageViews.length;
        this.swapChainFramebuffers = new long[imageCount];

        try (var stack = stackPush()) {
            final var extent = swapChain.getExtent();
            final var pFramebuffer = stack.mallocLong(1);
            for (var i = 0; i < imageCount; ++i) {
                final var pAttachments = stack.mallocLong(1);
                pAttachments.put(0, swapChainImageViews[i]);
                final var createInfo = VkFramebufferCreateInfo
                        .callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                        .renderPass(graphicsPipeline.getRenderPass())
                        .pAttachments(pAttachments)
                        .width(extent.width())
                        .height(extent.height())
                        .layers(1);

                final var error = vkCreateFramebuffer(this.device, createInfo, null, pFramebuffer);
                if (error != VK_SUCCESS) {
                    throw new IllegalStateException("Failed to create framebuffer: "
                                                            + translateVulkanResult(error));
                }
                this.swapChainFramebuffers[i] = pFramebuffer.get(0);
            }
        }

        this.cleanedUp = false;
    }

    /**
     * Releases the framebuffers in preparations for re-create or shutdown.
     */
    public void cleanup() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried to cleanup already cleared framebuffers!");
        }

        for (final var framebuffer : this.swapChainFramebuffers) {
            vkDestroyFramebuffer(this.device, framebuffer, null);
        }
        this.cleanedUp = true;
    }

    @Override
    public void close() {
        cleanup();
    }
}
