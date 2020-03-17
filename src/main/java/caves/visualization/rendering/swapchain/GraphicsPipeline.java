package caves.visualization.rendering.swapchain;

import caves.visualization.Vertex;
import caves.visualization.rendering.uniform.UniformBufferObject;
import caves.visualization.window.VKUtil;
import org.lwjgl.vulkan.*;

import java.io.IOException;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;

public final class GraphicsPipeline implements RecreatedWithSwapChain {
    /**
     * Index of the color attachment. This must match the value of the fragment shader color output
     * layout definition.
     * <p>
     * e.g. as the value is zero, the shader must define
     * <pre><code>
     *     layout(location = 0) out vec4 outColor;
     * </code></pre>
     */
    private static final int COLOR_ATTACHMENT_INDEX = 0;

    private final VkDevice device;
    private final SwapChain swapChain;
    private final UniformBufferObject uniformBufferObject;
    private long pipelineLayout;
    private long pipeline;
    private long renderPass;

    private boolean cleanedUp;

    /**
     * Gets the render pass.
     *
     * @return the render pass
     */
    public long getRenderPass() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried to fetch render pass from cleaned up pipeline!");
        }

        return this.renderPass;
    }

    /**
     * Gets the pipeline handle.
     *
     * @return the pipeline handle
     */
    public long getHandle() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried to fetch handle from cleaned up pipeline!");
        }

        return this.pipeline;
    }

    /**
     * Gets handle to the graphics pipeline layout.
     *
     * @return the pipeline layout
     */
    public long getPipelineLayout() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried to fetch pipeline layout from cleaned up pipeline!");
        }

        return this.pipelineLayout;
    }

    /**
     * Creates a new graphics pipeline.
     *
     * @param device              logical device to use
     * @param swapChain           the swapchain used for rendering
     * @param uniformBufferObject the uniform buffer object to use for uniforms
     */
    public GraphicsPipeline(
            final VkDevice device,
            final SwapChain swapChain,
            final UniformBufferObject uniformBufferObject
    ) {
        this.device = device;
        this.swapChain = swapChain;
        this.uniformBufferObject = uniformBufferObject;
        this.cleanedUp = true;

        recreate();
    }

    private static long createPipelineLayout(final VkDevice device, final UniformBufferObject ubo) {
        try (var stack = stackPush()) {
            final var pushConstantRanges = VkPushConstantRange.callocStack(0);
            final var pipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo
                    .callocStack()
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(ubo.getDescriptorSetLayout()))
                    .pPushConstantRanges(pushConstantRanges);

            final var pLayout = stack.mallocLong(1);
            vkCreatePipelineLayout(device, pipelineLayoutCreateInfo, null, pLayout);
            return pLayout.get(0);
        }
    }

    private static VkPipelineColorBlendStateCreateInfo createColorBlendInfo() {
        final var attachments = VkPipelineColorBlendAttachmentState.callocStack(1);
        attachments.get(0)
                   .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
                                           | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                   .blendEnable(true)
                   .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                   .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                   .colorBlendOp(VK_BLEND_OP_ADD)
                   .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                   .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                   .alphaBlendOp(VK_BLEND_OP_ADD);

        return VkPipelineColorBlendStateCreateInfo
                .callocStack()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                .logicOpEnable(false)
                .pAttachments(attachments);
    }

    private static VkPipelineMultisampleStateCreateInfo createMultisampleState() {
        return VkPipelineMultisampleStateCreateInfo
                .callocStack()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .sampleShadingEnable(false)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                .minSampleShading(1.0f)
                .pSampleMask(null)
                .alphaToCoverageEnable(false)
                .alphaToOneEnable(false);
    }

    private static VkPipelineRasterizationStateCreateInfo createRasterizationState() {
        return VkPipelineRasterizationStateCreateInfo
                .callocStack()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .depthClampEnable(false)
                .rasterizerDiscardEnable(false)
                .polygonMode(VK_POLYGON_MODE_FILL)
                .lineWidth(1.0f)
                .cullMode(VK_CULL_MODE_BACK_BIT)
                .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                .depthBiasEnable(false)
                .depthBiasConstantFactor(0.0f)
                .depthBiasClamp(0.0f)
                .depthBiasSlopeFactor(0.0f);
    }

    private static VkPipelineViewportStateCreateInfo createViewportState(final SwapChain swapChain) {
        final var viewports = createViewport(swapChain.getExtent());
        final var scissors = createScissorRect(swapChain.getExtent());

        return VkPipelineViewportStateCreateInfo.callocStack()
                                                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                                                .viewportCount(1)
                                                .pViewports(viewports)
                                                .scissorCount(1)
                                                .pScissors(scissors);
    }

    private static VkRect2D.Buffer createScissorRect(final VkExtent2D swapChainExtent) {
        final var scissors = VkRect2D.callocStack(1);
        scissors.get(0).offset().set(0, 0);
        scissors.get(0).extent(swapChainExtent);

        return scissors;
    }

    private static VkViewport.Buffer createViewport(final VkExtent2D swapChainExtent) {
        final var viewports = VkViewport.callocStack(1);
        viewports.get(0)
                 .minDepth(0.0f)
                 .maxDepth(1.0f)
                 .x(0.0f)
                 .y(0.0f)
                 .width(swapChainExtent.width())
                 .height(swapChainExtent.height());
        return viewports;
    }

    private static VkPipelineInputAssemblyStateCreateInfo createInputAssembly() {
        return VkPipelineInputAssemblyStateCreateInfo
                .callocStack()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(VK_PRIMITIVE_TOPOLOGY_POINT_LIST)
                .primitiveRestartEnable(false);
    }

    private static VkPipelineVertexInputStateCreateInfo createVertexInputInfo() {
        final var pVertexBindingDescriptions = VkVertexInputBindingDescription.callocStack(1);
        pVertexBindingDescriptions.put(0, Vertex.BINDING_DESCRIPTION);

        final var pVertexAttributeDescriptions = VkVertexInputAttributeDescription.callocStack(2);
        pVertexAttributeDescriptions.put(0, Vertex.ATTRIBUTE_DESCRIPTIONS[0]);
        pVertexAttributeDescriptions.put(1, Vertex.ATTRIBUTE_DESCRIPTIONS[1]);

        return VkPipelineVertexInputStateCreateInfo
                .callocStack()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexAttributeDescriptions(pVertexAttributeDescriptions)
                .pVertexBindingDescriptions(pVertexBindingDescriptions);
    }

    private long createRenderPass(final int swapChainImageFormat) {
        final var attachments = VkAttachmentDescription.callocStack(1);
        attachments.get(COLOR_ATTACHMENT_INDEX)
                   .format(swapChainImageFormat)
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

        try (var stack = stackPush()) {
            final var pRenderPass = stack.mallocLong(1);
            final var error = vkCreateRenderPass(this.device, renderPassInfo, null, pRenderPass);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Creating render pass failed: "
                                                        + translateVulkanResult(error));
            }

            return pRenderPass.get(0);
        }
    }

    /**
     * Re-creates the whole pipeline from scratch. {@link #cleanup()} must be called first to clean
     * up existing pipeline before re-creating.
     */
    @Override
    public void recreate() {
        if (!this.cleanedUp) {
            throw new IllegalStateException("Tried to re-create a graphics pipeline without cleaning up first!");
        }

        try (var stack = stackPush()) {
            final var vertexShaderStage = VKUtil.loadShader(device,
                                                            "shaders/shader.vert",
                                                            VK_SHADER_STAGE_VERTEX_BIT);
            final var fragmentShaderStage = VKUtil.loadShader(device,
                                                              "shaders/shader.frag",
                                                              VK_SHADER_STAGE_FRAGMENT_BIT);
            final var vertexShaderModule = vertexShaderStage.module();
            final var fragmentShaderModule = fragmentShaderStage.module();

            final var shaderStages = VkPipelineShaderStageCreateInfo.mallocStack(2);
            shaderStages.put(0, vertexShaderStage);
            shaderStages.put(1, fragmentShaderStage);

            final var vertexInputInfo = createVertexInputInfo();
            final var inputAssembly = createInputAssembly();
            final var viewportState = createViewportState(this.swapChain);
            final var rasterizer = createRasterizationState();
            final var multisampling = createMultisampleState();
            final var colorBlend = createColorBlendInfo();

            this.pipelineLayout = createPipelineLayout(this.device, this.uniformBufferObject);
            this.renderPass = createRenderPass(this.swapChain.getImageFormat());

            final var pipelineInfos = VkGraphicsPipelineCreateInfo.callocStack(1);
            pipelineInfos.get(0)
                         .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                         // Shader stages
                         .pStages(shaderStages)
                         // Fixed function state
                         .pVertexInputState(vertexInputInfo)
                         .pInputAssemblyState(inputAssembly)
                         .pViewportState(viewportState)
                         .pRasterizationState(rasterizer)
                         .pMultisampleState(multisampling)
                         //.pDepthStencilState(XXX);
                         .pColorBlendState(colorBlend)
                         //.pDynamicState(dynamicState)
                         // Layout, render pass, etc.
                         .layout(this.pipelineLayout)
                         .renderPass(this.renderPass)
                         .subpass(0)
                         // Base pipeline (if derived)
                         .basePipelineHandle(VK_NULL_HANDLE)
                         .basePipelineIndex(-1);

            final var pPipeline = stack.mallocLong(1);
            final var error = vkCreateGraphicsPipelines(this.device,
                                                        VK_NULL_HANDLE,
                                                        pipelineInfos,
                                                        null,
                                                        pPipeline);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Creating graphics pipeline failed: "
                                                        + translateVulkanResult(error));
            }
            this.pipeline = pPipeline.get(0);

            vkDestroyShaderModule(this.device, vertexShaderModule, null);
            vkDestroyShaderModule(this.device, fragmentShaderModule, null);
        } catch (final IOException e) {
            throw new IllegalStateException("Loading shader failed: " + e.getMessage());
        }

        this.cleanedUp = false;
    }

    /**
     * Releases the pipeline in preparation to re-creation or shutdown.
     */
    @Override
    public void cleanup() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried to cleanup an already cleared graphics pipeline!");
        }

        vkDestroyPipeline(this.device, this.pipeline, null);
        vkDestroyPipelineLayout(this.device, this.pipelineLayout, null);
        vkDestroyRenderPass(this.device, this.renderPass, null);
        this.cleanedUp = true;
    }
}
