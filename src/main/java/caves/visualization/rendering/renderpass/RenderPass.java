package caves.visualization.rendering.renderpass;

import caves.visualization.rendering.swapchain.RecreatedWithSwapChain;
import caves.visualization.rendering.swapchain.SwapChain;
import caves.visualization.window.DeviceContext;
import org.lwjgl.vulkan.*;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;

public final class RenderPass implements RecreatedWithSwapChain {
    /**
     * Index of the color attachment. This must match the value of the fragment shader color output
     * layout definition.
     * <p>
     * e.g. as the value is zero, the shader must define
     * <pre><code>
     *     layout(location = 0) out vec4 outColor;
     * </code></pre>
     */
    public static final int COLOR_ATTACHMENT_INDEX = 0;

    private final VkDevice device;
    private final SwapChain swapChain;

    private long handle;

    private boolean cleanedUp;

    /**
     * Gets a handle to this render pass. The value is invalidated on swapchain re-creation.
     *
     * @return the render pass handle
     */
    public long getHandle() {
        assert !this.cleanedUp : "Cannot get handle of a cleaned up render pass!";
        return this.handle;
    }

    /**
     * Creates a new render pass.
     *
     * @param deviceContext device to render on
     * @param swapChain     swapchain to render to
     */
    public RenderPass(final DeviceContext deviceContext, final SwapChain swapChain) {
        this.device = deviceContext.getDeviceHandle();
        this.swapChain = swapChain;
        this.cleanedUp = true;

        recreate();
    }

    /**
     * Recreates the render pass.
     *
     * @implNote it would be possible to skip the recreation in the common case where image
     *         format has not changed. However, that requires not even cleaning up in the first
     *         place in cases where the format does not change. That in turn would require
     *         re-designing the whole cleanup/(re)creation procedure.
     */
    @Override
    public void recreate() {
        assert this.cleanedUp : "Cannot re-create render pass before it is cleaned up!";

        try (var stack = stackPush()) {
            final var attachments = VkAttachmentDescription.callocStack(1);
            attachments.get(COLOR_ATTACHMENT_INDEX)
                       .format(this.swapChain.getImageFormat())
                       .samples(VK_SAMPLE_COUNT_1_BIT)
                       .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)             // Begin by clearing the image
                       .storeOp(VK_ATTACHMENT_STORE_OP_STORE)           // Store the result
                       .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)  // The stencil will be ignored
                       .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                       .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)        // We are going to clear the image anyway
                       .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);   // We promise to produce a presentable image

            final var colorAttachmentRefs = VkAttachmentReference.callocStack(1);
            colorAttachmentRefs.get(0)
                               .attachment(COLOR_ATTACHMENT_INDEX)
                               .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            final var subpasses = VkSubpassDescription.callocStack(1);
            subpasses.get(0)
                     .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                     .colorAttachmentCount(1)
                     .pColorAttachments(colorAttachmentRefs);

            final var dependencies = VkSubpassDependency.callocStack(1);
            dependencies.get(0)
                        .srcSubpass(VK_SUBPASS_EXTERNAL)
                        .dstSubpass(0)
                        .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                        .srcAccessMask(0)
                        .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                        .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            final var renderPassInfo = VkRenderPassCreateInfo
                    .callocStack()
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(attachments)
                    .pSubpasses(subpasses)
                    .pDependencies(dependencies);

            final var pRenderPass = stack.mallocLong(1);
            final var error = vkCreateRenderPass(this.device, renderPassInfo, null, pRenderPass);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Creating render pass failed: "
                                                        + translateVulkanResult(error));
            }

            this.handle = pRenderPass.get(0);
        }

        this.cleanedUp = false;
    }

    /**
     * Begins the render pass. Returns a {@link RenderPassScope scope} which must be closed in order
     * to end the pass. Failing to do so is considered a fatal error.
     * <p>
     * Intended use is with try-with-resources.
     *
     * @param commandBuffer command buffer to use
     * @param framebuffer   framebuffer to use
     * @param renderArea    area to render to
     * @param clearValues   attachment clear values
     *
     * @return auto-closeable render pass scope to be used with try-with-resources
     */
    public RenderPassScope begin(
            final VkCommandBuffer commandBuffer,
            final long framebuffer,
            final VkRect2D renderArea,
            final VkClearValue.Buffer clearValues
    ) {
        return new RenderPassScope(commandBuffer, this, framebuffer, renderArea, clearValues);
    }

    @Override
    public void cleanup() {
        assert !this.cleanedUp : "Cannot clean up an already cleared render pass!";
        vkDestroyRenderPass(this.device, this.handle, null);

        this.cleanedUp = true;
    }
}
