package caves.visualization.rendering;

import caves.visualization.rendering.mesh.Mesh;
import caves.visualization.rendering.renderpass.RenderPass;
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
    private final GraphicsPipeline pointPipeline;
    private final GraphicsPipeline linePipeline;
    private final RenderPass renderPass;
    private final Mesh lineMesh;
    private final UniformBufferObject ubo;
    private final Mesh pointMesh;

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
     * @param deviceContext the device context to use
     * @param commandPool   command pool to allocate on
     * @param swapChain     active swapchain
     * @param framebuffers  framebuffers to create the buffers for
     * @param pointPipeline the graphics pipeline for rendering meshes as points
     * @param linePipeline  the graphics pipeline for rendering meshes as line strips
     * @param renderPass    the render pass to record commands for
     * @param pointMesh     mesh to render as points
     * @param lineMesh      mesh to render as lines
     * @param ubo           the uniform buffer object to use for shader uniforms
     */
    public RenderCommandBuffers(
            final DeviceContext deviceContext,
            final CommandPool commandPool,
            final SwapChain swapChain,
            final Framebuffers framebuffers,
            final GraphicsPipeline pointPipeline,
            final GraphicsPipeline linePipeline,
            final RenderPass renderPass,
            final Mesh pointMesh,
            final Mesh lineMesh,
            final UniformBufferObject ubo
    ) {
        this.device = deviceContext.getDevice();
        this.commandPool = commandPool;
        this.swapChain = swapChain;
        this.framebuffers = framebuffers;
        this.pointPipeline = pointPipeline;
        this.linePipeline = linePipeline;
        this.renderPass = renderPass;
        this.pointMesh = pointMesh;
        this.lineMesh = lineMesh;
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
            clearValues.put(RenderPass.COLOR_ATTACHMENT_INDEX, getClearColor());

            try (var ignored = this.renderPass.begin(this.commandBuffers[imageIndex],
                                                     this.framebuffers.get(imageIndex),
                                                     renderArea,
                                                     clearValues)
            ) {
                vkCmdBindPipeline(this.commandBuffers[imageIndex],
                                  VK_PIPELINE_BIND_POINT_GRAPHICS,
                                  this.pointPipeline.getHandle());
                vkCmdBindDescriptorSets(this.commandBuffers[imageIndex],
                                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                                        this.pointPipeline.getPipelineLayout(),
                                        0,
                                        stack.longs(this.ubo.getDescriptorSet(imageIndex)),
                                        null);

                this.pointMesh.draw(this.commandBuffers[imageIndex]);

                vkCmdBindPipeline(this.commandBuffers[imageIndex],
                                  VK_PIPELINE_BIND_POINT_GRAPHICS,
                                  this.linePipeline.getHandle());
                vkCmdBindDescriptorSets(this.commandBuffers[imageIndex],
                                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                                        this.linePipeline.getPipelineLayout(),
                                        0,
                                        stack.longs(this.ubo.getDescriptorSet(imageIndex)),
                                        null);
                this.lineMesh.draw(this.commandBuffers[imageIndex]);
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
