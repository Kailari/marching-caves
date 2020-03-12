package caves.window.rendering;

import caves.window.DeviceContext;
import org.lwjgl.vulkan.*;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class CommandBuffers implements AutoCloseable {
    private static final int VERTEX_COUNT = 3;
    private static final int INSTANCE_COUNT = 1;
    private static final int A = 3;
    private static final int B = 2;
    private static final int G = 1;
    private static final int R = 0;

    private final VkDevice device;
    private final long commandPool;
    private final VertexBuffer vertexBuffer;

    private VkCommandBuffer[] commandBuffers;
    private boolean cleanedUp;

    /**
     * Creates new command pool and command buffers for all framebuffers.
     *
     * @param deviceContext    active device context
     * @param swapChain        active swapchain
     * @param framebuffers     framebuffers to create the buffers for
     * @param graphicsPipeline the graphics pipeline to use
     * @param vertexBuffer
     */
    public CommandBuffers(
            final DeviceContext deviceContext,
            final SwapChain swapChain,
            final Framebuffers framebuffers,
            final GraphicsPipeline graphicsPipeline,
            final VertexBuffer vertexBuffer
    ) {
        this.device = deviceContext.getDevice();
        this.vertexBuffer = vertexBuffer;

        this.commandPool = createGraphicsCommandPool(deviceContext);
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

    /**
     * Re-creates the command buffers. Re-uses the existing command pool.
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
            throw new IllegalStateException("Tried to re-create command buffers without cleaning up first!");
        }

        this.commandBuffers = allocateCommandBuffers(this.device, swapChain, this.commandPool);

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

        this.cleanedUp = false;
    }

    @Override
    public void close() {
        vkDestroyCommandPool(this.device, this.commandPool, null);
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
     * Releases command buffers in preparations for re-creation or shutdown.
     */
    public void cleanup() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried fetch image views from cleaned up swapchain!");
        }
        for (final var commandBuffer : this.commandBuffers) {
            vkFreeCommandBuffers(this.device, this.commandPool, commandBuffer);
        }
        this.cleanedUp = true;
    }
}
