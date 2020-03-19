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
    /**
     * Index of the depth attachment.
     *
     * @see #COLOR_ATTACHMENT_INDEX
     */
    public static final int DEPTH_ATTACHMENT_INDEX = 1;

    private static final int A = 3;
    private static final int B = 2;
    private static final int G = 1;
    private static final int R = 0;

    private final DeviceContext deviceContext;
    private final SwapChain swapChain;

    private final VkRect2D renderArea;
    private final VkClearValue.Buffer clearValues;

    private long handle;
    private Framebuffers framebuffers;

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

    private static VkClearValue getClearColor() {
        final var clearColor = VkClearValue.callocStack();
        clearColor.color()
                  .float32(R, 0.0f)
                  .float32(G, 0.0f)
                  .float32(B, 0.0f)
                  .float32(A, 1.0f);
        return clearColor;
    }

    private static VkClearValue getDepthClearValue() {
        final var depthValue = VkClearValue.callocStack();
        depthValue.depthStencil().set(1.0f, 0);
        return depthValue;
    }

    /**
     * Creates a new render pass.
     *
     * @param deviceContext device to render on
     * @param swapChain     swapchain to render to
     */
    public RenderPass(
            final DeviceContext deviceContext,
            final SwapChain swapChain
    ) {
        this.deviceContext = deviceContext;
        this.swapChain = swapChain;

        this.renderArea = VkRect2D.calloc();
        this.renderArea.offset().set(0, 0);
        this.renderArea.extent().set(this.swapChain.getExtent());

        this.clearValues = VkClearValue.calloc(2);
        this.clearValues.put(RenderPass.COLOR_ATTACHMENT_INDEX, getClearColor());
        this.clearValues.put(RenderPass.DEPTH_ATTACHMENT_INDEX, getDepthClearValue());

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

        this.renderArea.extent().set(this.swapChain.getExtent());

        final var depthBufferImageFormat = DepthBuffer.findDepthFormat(this.deviceContext);
        try (var stack = stackPush()) {
            final var attachments = VkAttachmentDescription.callocStack(2);
            attachments.get(COLOR_ATTACHMENT_INDEX)
                       .format(this.swapChain.getImageFormat())
                       .samples(VK_SAMPLE_COUNT_1_BIT)
                       .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)             // Begin by clearing the image
                       .storeOp(VK_ATTACHMENT_STORE_OP_STORE)           // Store the result
                       .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)  // The stencil will be ignored
                       .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                       .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)        // We are going to clear the image anyway
                       .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);   // We promise to produce a presentable image
            attachments.get(DEPTH_ATTACHMENT_INDEX)
                       .format(depthBufferImageFormat)
                       .samples(VK_SAMPLE_COUNT_1_BIT)
                       .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                       .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                       .stencilLoadOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                       .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                       .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                       .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);


            final var colorAttachmentRefs = VkAttachmentReference.callocStack(1);
            colorAttachmentRefs.get(0)
                               .attachment(COLOR_ATTACHMENT_INDEX)
                               .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            final var depthAttachmentRef = VkAttachmentReference
                    .callocStack()
                    .attachment(DEPTH_ATTACHMENT_INDEX)
                    .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            final var subpasses = VkSubpassDescription.callocStack(1);
            subpasses.get(0)
                     .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                     .colorAttachmentCount(1)
                     .pColorAttachments(colorAttachmentRefs)
                     .pDepthStencilAttachment(depthAttachmentRef);

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
            final var error = vkCreateRenderPass(this.deviceContext.getDeviceHandle(),
                                                 renderPassInfo,
                                                 null,
                                                 pRenderPass);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Creating render pass failed: "
                                                        + translateVulkanResult(error));
            }

            this.handle = pRenderPass.get(0);
        }

        this.framebuffers = new Framebuffers(this.deviceContext,
                                             this.handle,
                                             this.swapChain,
                                             depthBufferImageFormat);

        this.cleanedUp = false;
    }

    /**
     * Begins the render pass. Returns a {@link RenderPassScope scope} which must be closed in order
     * to end the pass. Failing to do so is considered a fatal error.
     * <p>
     * Intended use is with try-with-resources.
     *
     * @param commandBuffer command buffer to use
     * @param imageIndex    index of the swapchain image in use
     *
     * @return auto-closeable render pass scope to be used with try-with-resources
     */
    public RenderPassScope begin(
            final VkCommandBuffer commandBuffer,
            final int imageIndex
    ) {
        return new RenderPassScope(commandBuffer,
                                   this,
                                   this.framebuffers.get(imageIndex),
                                   this.renderArea,
                                   this.clearValues);
    }

    @Override
    public void cleanup() {
        assert !this.cleanedUp : "Cannot clean up an already cleared render pass!";
        this.framebuffers.close();
        vkDestroyRenderPass(this.deviceContext.getDeviceHandle(), this.handle, null);

        this.cleanedUp = true;
    }
}
