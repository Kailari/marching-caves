package caves.visualization.window.rendering;

import caves.visualization.window.DeviceContext;
import caves.visualization.window.rendering.swapchain.Framebuffers;
import caves.visualization.window.rendering.swapchain.GraphicsPipeline;
import caves.visualization.window.rendering.swapchain.SwapChain;
import org.lwjgl.vulkan.*;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class RenderCommandBuffers implements AutoCloseable {
    private static final int A = 3;
    private static final int B = 2;
    private static final int G = 1;
    private static final int R = 0;

    private final VkDevice device;
    private final CommandPool commandPool;
    private final GPUBuffer<GraphicsPipeline.Vertex> vertexBuffer;
    private final GPUBuffer<Short> indexBuffer;

    private VkCommandBuffer[] commandBuffers;
    private boolean cleanedUp;

    /**
     * Creates new render command buffers for rendering the given fixed vertex buffer.
     *
     * @param deviceContext    the device context to use
     * @param commandPool      command pool to allocate on
     * @param swapChain        active swapchain
     * @param framebuffers     framebuffers to create the buffers for
     * @param graphicsPipeline the graphics pipeline to use
     * @param vertexBuffer     vertices to use for rendering
     * @param indexBuffer      indices to use for rendering
     */
    public RenderCommandBuffers(
            final DeviceContext deviceContext,
            final CommandPool commandPool,
            final SwapChain swapChain,
            final Framebuffers framebuffers,
            final GraphicsPipeline graphicsPipeline,
            final GPUBuffer<GraphicsPipeline.Vertex> vertexBuffer,
            final GPUBuffer<Short> indexBuffer
    ) {
        this.device = deviceContext.getDevice();
        this.commandPool = commandPool;
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;

        this.cleanedUp = true;

        recreate(swapChain, framebuffers, graphicsPipeline);
    }

    private static VkCommandBuffer[] allocateCommandBuffers(
            final VkDevice device,
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
            final var error = vkAllocateCommandBuffers(device, allocInfo, pBuffers);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Allocating command buffers failed: "
                                                        + translateVulkanResult(error));
            }
            final var buffers = new VkCommandBuffer[bufferCount];
            for (var i = 0; i < bufferCount; ++i) {
                buffers[i] = new VkCommandBuffer(pBuffers.get(i), device);
            }
            return buffers;
        }
    }

    /**
     * Gets the command buffer for framebuffer/image view with given index.
     *
     * @param imageIndex index of the image view or framebuffer to use
     *
     * @return the command buffer
     */
    public VkCommandBuffer getBufferForImage(final int imageIndex) {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried to fetch buffer from cleaned up command buffers!");
        }
        return this.commandBuffers[imageIndex];
    }

    /**
     * Re-creates the command buffers.
     *
     * @param swapChain        swapchain to use
     * @param framebuffers     framebuffers to use
     * @param graphicsPipeline graphics pipeline to use
     */
    public void recreate(
            final SwapChain swapChain,
            final Framebuffers framebuffers,
            final GraphicsPipeline graphicsPipeline
    ) {
        if (!this.cleanedUp) {
            throw new IllegalStateException("Tried to recreate render command buffers not cleared!");
        }

        this.commandBuffers = allocateCommandBuffers(this.device, swapChain, this.commandPool.getHandle());

        for (var i = 0; i < this.commandBuffers.length; ++i) {
            try (var stack = stackPush()) {
                final var beginInfo = VkCommandBufferBeginInfo
                        .callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

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

                final var vertexBuffers = stack.mallocLong(1);
                vertexBuffers.put(this.vertexBuffer.getBufferHandle());
                vertexBuffers.flip();

                final var offsets = stack.mallocLong(1);
                offsets.put(0L);
                offsets.flip();

                vkCmdBindVertexBuffers(this.commandBuffers[i], 0, vertexBuffers, offsets);
                vkCmdBindIndexBuffer(this.commandBuffers[i],
                                     this.indexBuffer.getBufferHandle(),
                                     0,
                                     VK_INDEX_TYPE_UINT16);
                vkCmdDrawIndexed(this.commandBuffers[i],
                                 this.indexBuffer.getElementCount(),
                                 1,
                                 0,
                                 0,
                                 0);

                // End the pass/buffer
                vkCmdEndRenderPass(this.commandBuffers[i]);
                error = vkEndCommandBuffer(this.commandBuffers[i]);
                if (error != VK_SUCCESS) {
                    throw new IllegalStateException("Failed to finalize recording a command buffer: "
                                                            + translateVulkanResult(error));
                }
            }
        }

        this.cleanedUp = false;
    }

    /**
     * Releases command buffers in preparations for re-creation or shutdown.
     */
    public void cleanup() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried to cleanup render command buffers while already cleared!");
        }

        for (final var commandBuffer : this.commandBuffers) {
            vkFreeCommandBuffers(this.device, this.commandPool.getHandle(), commandBuffer);
        }

        this.cleanedUp = true;
    }

    @Override
    public void close() {
        this.cleanup();
    }
}
