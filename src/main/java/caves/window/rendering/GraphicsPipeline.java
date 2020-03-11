package caves.window.rendering;

import caves.window.VKUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.io.IOException;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;

public final class GraphicsPipeline implements AutoCloseable {
    private final VkDevice device;

    private final long pipelineLayout;
    private final long pipeline;
    private final long renderPass;

    /**
     * Creates a new graphics pipeline.
     *
     * @param device    logical device to use
     * @param swapChain the swapchain used for rendering
     */
    public GraphicsPipeline(final VkDevice device, final SwapChain swapChain) {
        this.device = device;

        try (var stack = stackPush()) {
            final var vertexShaderStage = VKUtil.loadShader(device,
                                                            "shaders/shader.vert",
                                                            VK_SHADER_STAGE_VERTEX_BIT);
            final var fragmentShaderStage = VKUtil.loadShader(device,
                                                              "shaders/shader.frag",
                                                              VK_SHADER_STAGE_FRAGMENT_BIT);
            final var vertexShaderModule = vertexShaderStage.module();
            final var fragmentShaderModule = fragmentShaderStage.module();

            final var shaderStages = VkPipelineShaderStageCreateInfo.mallocStack(2, stack);
            shaderStages.put(vertexShaderStage);
            shaderStages.put(fragmentShaderStage);
            shaderStages.flip();

            final var vertexInputInfo = createVertexInputInfo(stack);
            final var inputAssembly = createInputAssembly(stack);
            final var viewport = createViewport(stack, swapChain.getExtent());
            final var scissor = createScissorRect(stack, swapChain.getExtent());
            final var viewportState = createViewportState(stack, viewport, scissor);
            final var rasterizer = createRasterizationState(stack);
            final var multisampling = createMultisampleState(stack);
            // TODO: depth/stencilState
            final var colorBlend = createColorBlendInfo(stack);
            final var dynamicState = createDynamicState(stack);

            this.pipelineLayout = createPipelineLayout(stack, device);

            this.renderPass = createRenderPass(stack, swapChain.getImageFormat());

            final var pipelineInfos = VkGraphicsPipelineCreateInfo
                    .callocStack(1, stack);
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
                         .pDynamicState(dynamicState)
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
    }

    private static long createPipelineLayout(
            final MemoryStack stack,
            final VkDevice device
    ) {
        final var pLayouts = stack.mallocLong(0);
        final var pushConstantRanges = VkPushConstantRange.callocStack(0);
        final var pipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo
                .callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(pLayouts)
                .pPushConstantRanges(pushConstantRanges);


        final var pLayout = stack.mallocLong(1);
        vkCreatePipelineLayout(device, pipelineLayoutCreateInfo, null, pLayout);
        return pLayout.get(0);
    }

    private static VkPipelineColorBlendStateCreateInfo createColorBlendInfo(
            final MemoryStack stack
    ) {
        final var attachments = VkPipelineColorBlendAttachmentState.callocStack(1, stack);
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
                .callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                .logicOpEnable(false)
                .pAttachments(attachments);
    }

    private static VkPipelineMultisampleStateCreateInfo createMultisampleState(final MemoryStack stack) {
        return VkPipelineMultisampleStateCreateInfo
                .callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .sampleShadingEnable(true)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                .minSampleShading(1.0f)
                .pSampleMask(null)
                .alphaToCoverageEnable(false)
                .alphaToOneEnable(false);
    }

    private static VkPipelineRasterizationStateCreateInfo createRasterizationState(final MemoryStack stack) {
        return VkPipelineRasterizationStateCreateInfo
                .callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .depthClampEnable(false)
                .rasterizerDiscardEnable(false)
                .polygonMode(VK_POLYGON_MODE_FILL)
                .lineWidth(1.0f)
                .cullMode(VK_CULL_MODE_BACK_BIT)
                .frontFace(VK_FRONT_FACE_CLOCKWISE)
                .depthBiasEnable(false)
                .depthBiasConstantFactor(0.0f)
                .depthBiasClamp(0.0f)
                .depthBiasSlopeFactor(0.0f);
    }

    private static VkPipelineViewportStateCreateInfo createViewportState(
            final MemoryStack stack,
            final VkViewport.Buffer viewports,
            final VkRect2D.Buffer scissors
    ) {
        return VkPipelineViewportStateCreateInfo.callocStack(stack)
                                                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                                                .viewportCount(1)
                                                .pViewports(viewports)
                                                .scissorCount(1)
                                                .pScissors(scissors);
    }

    private static VkRect2D.Buffer createScissorRect(
            final MemoryStack stack,
            final VkExtent2D swapChainExtent
    ) {
        final var scissors = VkRect2D.callocStack(1, stack);
        scissors.get(0).offset().set(0, 0);
        scissors.get(0).extent(swapChainExtent);

        return scissors;
    }

    private static VkViewport.Buffer createViewport(
            final MemoryStack stack,
            final VkExtent2D swapChainExtent
    ) {
        final var viewports = VkViewport.callocStack(1, stack);
        viewports.get(0)
                 .minDepth(0.0f)
                 .maxDepth(1.0f)
                 .x(0.0f)
                 .y(0.0f)
                 .width(swapChainExtent.width())
                 .height(swapChainExtent.height());
        return viewports;
    }

    private static VkPipelineInputAssemblyStateCreateInfo createInputAssembly(final MemoryStack stack) {
        return VkPipelineInputAssemblyStateCreateInfo
                .callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .primitiveRestartEnable(false);
    }

    private static VkPipelineVertexInputStateCreateInfo createVertexInputInfo(final MemoryStack stack) {
        final var pVertexAttributeDescriptions = VkVertexInputAttributeDescription
                .callocStack(0, stack);
        final var pVertexBindingDescriptions = VkVertexInputBindingDescription
                .callocStack(0, stack);

        return VkPipelineVertexInputStateCreateInfo
                .callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexAttributeDescriptions(pVertexAttributeDescriptions)
                .pVertexBindingDescriptions(pVertexBindingDescriptions);
    }

    private static VkPipelineDynamicStateCreateInfo createDynamicState(final MemoryStack stack) {
        final var pDynamicStates = stack.mallocInt(2);
        pDynamicStates.put(0, VK_DYNAMIC_STATE_VIEWPORT);
        pDynamicStates.put(0, VK_DYNAMIC_STATE_LINE_WIDTH);
        pDynamicStates.flip();

        return VkPipelineDynamicStateCreateInfo
                .callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                .pDynamicStates(pDynamicStates);
    }

    private long createRenderPass(final MemoryStack stack, final int swapChainImageFormat) {
        final var attachments = VkAttachmentDescription.callocStack(1, stack);
        attachments.get(0) // color attachment
                   .format(swapChainImageFormat)
                   .samples(VK_SAMPLE_COUNT_1_BIT)
                   .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)             // Begin by clearing the image
                   .storeOp(VK_ATTACHMENT_STORE_OP_STORE)           // Store the result
                   .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)  // The stencil will be ignored
                   .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                   .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)        // We are going to clear the image anyway
                   .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);   // We promise to produce a presentable image

        final var colorAttachmentRefs = VkAttachmentReference.callocStack(1, stack);
        colorAttachmentRefs.get(0)
                           .attachment(0)  // our color attachment is at index zero
                           .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

        // NOTE:    Color attachment is at index zero, this is directly reflected in the fragment
        //          shader as the `layout(location = 0)` on fragment color output!

        final var subpasses = VkSubpassDescription.callocStack(1, stack);
        subpasses.get(0)
                 .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                 .colorAttachmentCount(1)
                 .pColorAttachments(colorAttachmentRefs);

        final var pCreateInfo = VkRenderPassCreateInfo
                .callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                .pAttachments(attachments)
                .pSubpasses(subpasses);

        final var pRenderPass = stack.mallocLong(1);
        final var error = vkCreateRenderPass(this.device, pCreateInfo, null, pRenderPass);
        if (error != VK_SUCCESS) {
            throw new IllegalStateException("Creating render pass failed: "
                                                    + translateVulkanResult(error));
        }

        return pRenderPass.get(0);
    }

    @Override
    public void close() {
        vkDestroyPipeline(this.device, this.pipeline, null);
        vkDestroyPipelineLayout(this.device, this.pipelineLayout, null);
        vkDestroyRenderPass(this.device, this.renderPass, null);
    }
}
