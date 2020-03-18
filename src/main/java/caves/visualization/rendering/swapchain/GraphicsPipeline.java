package caves.visualization.rendering.swapchain;

import caves.visualization.Vertex;
import caves.visualization.rendering.renderpass.RenderPass;
import caves.visualization.rendering.uniform.UniformBufferObject;
import caves.visualization.window.VKUtil;
import org.lwjgl.vulkan.*;

import java.io.IOException;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class GraphicsPipeline implements RecreatedWithSwapChain {
    private final VkDevice device;
    private final SwapChain swapChain;
    private final UniformBufferObject uniformBufferObject;
    private final RenderPass renderPass;

    private final int topology;

    private long pipelineLayout;
    private long pipeline;

    private boolean cleanedUp;

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
     * @param renderPass          the render pass this pipeline belongs to
     * @param uniformBufferObject the uniform buffer object to use for uniforms
     * @param topology            the input assembly topology to use
     */
    public GraphicsPipeline(
            final VkDevice device,
            final SwapChain swapChain,
            final RenderPass renderPass,
            final UniformBufferObject uniformBufferObject,
            final int topology
    ) {
        this.device = device;
        this.swapChain = swapChain;
        this.renderPass = renderPass;
        this.uniformBufferObject = uniformBufferObject;
        this.topology = topology;

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

    private static VkPipelineInputAssemblyStateCreateInfo createInputAssembly(final int topology) {
        return VkPipelineInputAssemblyStateCreateInfo
                .callocStack()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(topology)
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

    /**
     * Re-creates the whole pipeline from scratch. {@link #cleanup()} must be called first to clean
     * up existing pipeline before recreating.
     * <p>
     * Re-creation may be necessary for two reasons:
     * <ol>
     *     <li>The render pass has been invalidated due to swapchain image format changing
     *     <i>(requires the render pass to be recreated, happens very rarely)</i></li>
     *     <li>The swapchain extent has changed, requiring resizing the viewport
     *     <i>(the most common reason for swapchain recreation)</i></li>
     * </ol>
     * <p>
     * Current implementation always recreates the render pass, requiring the pipeline to be
     * recreated every time, too. However, if viewport extent was moved to dynamic state, we could
     * skip the second condition and add a check for the first, allowing completely skipping
     * the pipeline recreation in most cases!
     */
    @Override
    public void recreate() {
        if (!this.cleanedUp) {
            throw new IllegalStateException("Tried to re-create a graphics pipeline without cleaning up first!");
        }

        try (var stack = stackPush()) {
            final var vertexShaderStage = VKUtil.loadShader(this.device,
                                                            "shaders/shader.vert",
                                                            VK_SHADER_STAGE_VERTEX_BIT);
            final var fragmentShaderStage = VKUtil.loadShader(this.device,
                                                              "shaders/shader.frag",
                                                              VK_SHADER_STAGE_FRAGMENT_BIT);

            final var shaderStages = VkPipelineShaderStageCreateInfo.mallocStack(2)
                                                                    .put(vertexShaderStage)
                                                                    .put(fragmentShaderStage)
                                                                    .flip();

            final var vertexInputInfo = createVertexInputInfo();
            final var inputAssembly = createInputAssembly(this.topology);
            final var viewportState = createViewportState(this.swapChain);
            final var rasterizer = createRasterizationState();
            final var multisampling = createMultisampleState();
            final var colorBlend = createColorBlendInfo();

            this.pipelineLayout = createPipelineLayout(this.device, this.uniformBufferObject);

            final var pipelineInfos = VkGraphicsPipelineCreateInfo.callocStack(1);
            pipelineInfos.get(0)
                         .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                         .pStages(shaderStages)
                         .pVertexInputState(vertexInputInfo)
                         .pInputAssemblyState(inputAssembly)
                         .pViewportState(viewportState)
                         .pRasterizationState(rasterizer)
                         .pMultisampleState(multisampling)
                         .pColorBlendState(colorBlend)
                         //.pDepthStencilState(XXX);
                         //.pDynamicState(dynamicState)
                         .layout(this.pipelineLayout)
                         .renderPass(this.renderPass.getHandle())
                         .subpass(0);

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

            vkDestroyShaderModule(this.device, vertexShaderStage.module(), null);
            vkDestroyShaderModule(this.device, fragmentShaderStage.module(), null);
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
        this.cleanedUp = true;
    }
}
