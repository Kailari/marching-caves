package caves.window.rendering;

import caves.window.DeviceContext;
import org.lwjgl.PointerBuffer;
import org.lwjgl.vulkan.*;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class CommandBuffers implements AutoCloseable {
    public static final int VERTEX_COUNT = 3;
    public static final int INSTANCE_COUNT = 1;
    public static final int A = 3;
    public static final int B = 2;
    public static final int G = 1;
    public static final int R = 0;
    private final VkDevice device;

    private final long commandPool;
    private final VkCommandBuffer[] commandBuffers;

    public CommandBuffers(
            final DeviceContext deviceContext,
            final SwapChain swapChain,
            final Framebuffers framebuffers,
            final GraphicsPipeline graphicsPipeline
    ) {
        this.device = deviceContext.getDevice();

        this.commandPool = createGraphicsCommandPool(deviceContext);
        this.commandBuffers = allocateCommandBuffers(deviceContext, swapChain, this.commandPool);

        try (var stack = stackPush()) {
            for (var i = 0; i < this.commandBuffers.length; ++i) {
                final var beginInfo = VkCommandBufferBeginInfo
                        .callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                        .flags(0)
                        .pInheritanceInfo(null);

                // Begin buffer
                var error = vkBeginCommandBuffer(this.commandBuffers[i], beginInfo);
                if (error != VK_SUCCESS) {
                    throw new IllegalStateException("Failed to begin recording a command buffer: "
                                                            + translateVulkanResult(error));
                }

                // Begin pass
                final var clearColor = VkClearValue.callocStack(1, stack);
                clearColor.get(0).color()
                          .float32(R, 0.0f)
                          .float32(G, 0.0f)
                          .float32(B, 0.0f)
                          .float32(A, 1.0f);
                final var renderPassInfo = VkRenderPassBeginInfo
                        .callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                        .renderPass(graphicsPipeline.getRenderPass())
                        .framebuffer(framebuffers.getSwapChainFramebuffers()[i])
                        .pClearValues(clearColor);
                renderPassInfo.renderArea().offset().set(0, 0);
                renderPassInfo.renderArea().extent().set(swapChain.getExtent());
                vkCmdBeginRenderPass(this.commandBuffers[i], renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

                // Record render commands
                vkCmdBindPipeline(this.commandBuffers[i],
                                  VK_PIPELINE_BIND_POINT_GRAPHICS,
                                  graphicsPipeline.getGraphicsPipeline());
                vkCmdDraw(this.commandBuffers[i], VERTEX_COUNT, INSTANCE_COUNT, 0, 0);

                // End the pass/buffer
                vkCmdEndRenderPass(this.commandBuffers[i]);
                error = vkEndCommandBuffer(this.commandBuffers[i]);
                if (error != VK_SUCCESS) {
                    throw new IllegalStateException("Failed to finalize recording a command buffer: "
                                                            + translateVulkanResult(error));
                }
            }
        }
    }

    private static VkCommandBuffer[] allocateCommandBuffers(
            final DeviceContext deviceContext,
            final SwapChain swapChain,
            final long commandPool
    ) {
        try (var stack = stackPush()) {
            final var bufferCount = swapChain.getImageViews().length;
            final var allocInfo = VkCommandBufferAllocateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(bufferCount);

            final var pBuffers = stack.mallocPointer(bufferCount);
            final var error = vkAllocateCommandBuffers(deviceContext.getDevice(), allocInfo, pBuffers);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Allocating command buffers failed: "
                                                        + translateVulkanResult(error));
            }
            final var buffers = new VkCommandBuffer[bufferCount];
            for (var i = 0; i < bufferCount; ++i) {
                buffers[i] = new VkCommandBuffer(pBuffers.get(i), deviceContext.getDevice());
            }
            return buffers;
        }
    }

    private static long createGraphicsCommandPool(final DeviceContext deviceContext) {
        try (var stack = stackPush()) {
            final var pCreateInfo = VkCommandPoolCreateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .queueFamilyIndex(deviceContext.getGraphicsQueueFamilyIndex())
                    .flags(0); // No flags

            final var pCommandPool = stack.mallocLong(1);
            final var error = vkCreateCommandPool(deviceContext.getDevice(), pCreateInfo, null, pCommandPool);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create command pool: "
                                                        + translateVulkanResult(error));
            }

            return pCommandPool.get(0);
        }
    }

    @Override
    public void close() {
        vkDestroyCommandPool(this.device, this.commandPool, null);
    }

    public VkCommandBuffer getBufferForImage(final int imageIndex) {
        return this.commandBuffers[imageIndex];
    }
}
