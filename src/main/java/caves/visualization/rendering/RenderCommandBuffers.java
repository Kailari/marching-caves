package caves.visualization.rendering;

import caves.visualization.Vertex;
import caves.visualization.rendering.renderpass.RenderPassScope;
import caves.visualization.rendering.swapchain.Framebuffers;
import caves.visualization.rendering.swapchain.GraphicsPipeline;
import caves.visualization.rendering.swapchain.RecreatedWithSwapChain;
import caves.visualization.rendering.swapchain.SwapChain;
import caves.visualization.rendering.uniform.UniformBufferObject;
import caves.visualization.window.DeviceContext;
import org.lwjgl.vulkan.*;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class RenderCommandBuffers implements RecreatedWithSwapChain {
    private static final int A = 3;
    private static final int B = 2;
    private static final int G = 1;
    private static final int R = 0;

    private final VkDevice device;
    private final CommandPool commandPool;
    private final SwapChain swapChain;
    private final Framebuffers framebuffers;
    private final GraphicsPipeline graphicsPipeline;
    private final SequentialGPUBuffer<Vertex> vertexBuffer;
    private final SequentialGPUBuffer<Short> indexBuffer;
    private final UniformBufferObject ubo;

    private VkCommandBuffer[] commandBuffers;
    private boolean cleanedUp;

    private static VkClearValue getClearColor() {
        final var clearColor = VkClearValue.callocStack();
        clearColor.color()
                  .float32(R, 0.0f)
                  .float32(G, 0.0f)
                  .float32(B, 0.0f)
                  .float32(A, 1.0f);
        return clearColor;
    }

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
     * @param ubo              the uniform buffer object to use for shader uniforms
     */
    public RenderCommandBuffers(
            final DeviceContext deviceContext,
            final CommandPool commandPool,
            final SwapChain swapChain,
            final Framebuffers framebuffers,
            final GraphicsPipeline graphicsPipeline,
            final SequentialGPUBuffer<Vertex> vertexBuffer,
            final SequentialGPUBuffer<Short> indexBuffer,
            final UniformBufferObject ubo
    ) {
        this.device = deviceContext.getDevice();
        this.commandPool = commandPool;
        this.swapChain = swapChain;
        this.framebuffers = framebuffers;
        this.graphicsPipeline = graphicsPipeline;
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.ubo = ubo;

        this.cleanedUp = true;

        recreate();
    }

    private static VkCommandBuffer[] allocateCommandBuffers(
            final VkDevice device,
            final int bufferCount,
            final long commandPool
    ) {
        try (var stack = stackPush()) {
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
     */
    public void recreate() {
        if (!this.cleanedUp) {
            throw new IllegalStateException("Tried to recreate render command buffers not cleared!");
        }

        final var imageCount = this.swapChain.getImageCount();
        this.commandBuffers = allocateCommandBuffers(this.device,
                                                     imageCount,
                                                     this.commandPool.getHandle());

        for (var imageIndex = 0; imageIndex < imageCount; ++imageIndex) {
            createCommandBufferForImage(imageIndex);
        }

        this.cleanedUp = false;
    }

    private void createCommandBufferForImage(final int imageIndex) {
        beginBuffer(this.commandBuffers[imageIndex]);

        try (var stack = stackPush()) {
            final var renderArea = VkRect2D.callocStack();
            renderArea.offset().set(0, 0);
            renderArea.extent().set(this.swapChain.getExtent());

            final var clearValues = VkClearValue.callocStack(1, stack);
            clearValues.put(0, RenderCommandBuffers.getClearColor());
            // XXX: The index "0" is actually the color attachment index?

            try (var ignored = new RenderPassScope(this.commandBuffers[imageIndex],
                                                   this.graphicsPipeline,
                                                   this.framebuffers.get(imageIndex),
                                                   renderArea,
                                                   clearValues)
            ) {
                vkCmdBindPipeline(this.commandBuffers[imageIndex],
                                  VK_PIPELINE_BIND_POINT_GRAPHICS,
                                  this.graphicsPipeline.getHandle());

                vkCmdBindVertexBuffers(this.commandBuffers[imageIndex],
                                       0,
                                       stack.longs(this.vertexBuffer.getBufferHandle()),
                                       stack.longs(0L));
                vkCmdBindIndexBuffer(this.commandBuffers[imageIndex],
                                     this.indexBuffer.getBufferHandle(),
                                     0,
                                     VK_INDEX_TYPE_UINT16);

                vkCmdBindDescriptorSets(this.commandBuffers[imageIndex],
                                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                                        this.graphicsPipeline.getPipelineLayout(),
                                        0,
                                        stack.longs(this.ubo.getDescriptorSets()[imageIndex]),
                                        null);
                vkCmdDrawIndexed(this.commandBuffers[imageIndex],
                                 this.indexBuffer.getElementCount(),
                                 1,
                                 0,
                                 0,
                                 0);
            }
        }

        endBuffer(this.commandBuffers[imageIndex]);
    }

    private void endBuffer(final VkCommandBuffer commandBuffer) {
        final var error = vkEndCommandBuffer(commandBuffer);
        if (error != VK_SUCCESS) {
            throw new IllegalStateException("Failed to finalize recording a command buffer: "
                                                    + translateVulkanResult(error));
        }
    }

    private void beginBuffer(final VkCommandBuffer commandBuffer) {
        try (var stack = stackPush()) {
            final var beginInfo = VkCommandBufferBeginInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

            final var error = vkBeginCommandBuffer(commandBuffer, beginInfo);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Failed to begin recording a command buffer: "
                                                        + translateVulkanResult(error));
            }
        }
    }

    /**
     * Releases command buffers in preparations for re-creation or shutdown.
     */
    @Override
    public void cleanup() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried to cleanup render command buffers while already cleared!");
        }

        for (final var commandBuffer : this.commandBuffers) {
            vkFreeCommandBuffers(this.device, this.commandPool.getHandle(), commandBuffer);
        }

        this.cleanedUp = true;
    }

}
