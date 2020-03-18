package caves.visualization.rendering.swapchain;

import caves.visualization.rendering.DepthBuffer;
import caves.visualization.rendering.renderpass.RenderPass;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class Framebuffers implements RecreatedWithSwapChain {
    private final VkDevice device;
    private final RenderPass renderPass;
    private final SwapChain swapChain;
    private final DepthBuffer depthBuffer;

    private long[] swapChainFramebuffers;
    private boolean cleanedUp;

    /**
     * Creates framebuffers for the swapchain. Each swapchain image gets its own attached
     * framebuffer.
     *
     * @param device      logical device the swapchain is created on
     * @param renderPass  which render pass these framebuffers are used with
     * @param swapChain   the swapchain to use
     * @param depthBuffer depthbuffer to use
     */
    public Framebuffers(
            final VkDevice device,
            final RenderPass renderPass,
            final SwapChain swapChain,
            final DepthBuffer depthBuffer
    ) {
        this.device = device;
        this.renderPass = renderPass;
        this.swapChain = swapChain;
        this.depthBuffer = depthBuffer;

        this.cleanedUp = true;

        recreate();
    }

    /**
     * Gets the swapchain image framebuffers. Buffer with index <code>i</code> corresponds directly
     * to swapchain image view with index <code>i</code>
     *
     * @param imageIndex image index of the desired framebuffer
     *
     * @return handle for the framebuffer with the given image index
     */
    public long get(final int imageIndex) {
        assert !this.cleanedUp : "Tried to fetch framebuffers before re-creating!";
        return this.swapChainFramebuffers[imageIndex];
    }

    /**
     * Re-creates the framebuffers for all swapchain image views.
     * <p>
     * Re-creation is necessary because the framebuffers are directly dependent on swapchain image
     * views, which in turn are always invalidated on re-create.
     */
    @Override
    public void recreate() {
        assert this.cleanedUp : "Tried to re-create framebuffers without cleaning up!";

        final var swapChainImageViews = this.swapChain.getImageViews();
        final var imageCount = swapChainImageViews.length;
        this.swapChainFramebuffers = new long[imageCount];

        try (var stack = stackPush()) {
            final var extent = this.swapChain.getExtent();
            final var pFramebuffer = stack.mallocLong(1);
            for (var i = 0; i < imageCount; ++i) {
                final var pAttachments = stack.longs(swapChainImageViews[i],
                                                     this.depthBuffer.getImageView());
                final var createInfo = VkFramebufferCreateInfo
                        .callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                        .renderPass(this.renderPass.getHandle())
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
    @Override
    public void cleanup() {
        assert !this.cleanedUp : "Tried to cleanup already cleared framebuffers!";

        for (final var framebuffer : this.swapChainFramebuffers) {
            vkDestroyFramebuffer(this.device, framebuffer, null);
        }
        this.cleanedUp = true;
    }
}
