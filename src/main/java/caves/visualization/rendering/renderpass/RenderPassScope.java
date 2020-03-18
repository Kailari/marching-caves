package caves.visualization.rendering.renderpass;

import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class RenderPassScope implements AutoCloseable {
    private final VkCommandBuffer commandBuffer;

    /**
     * Initializes a new render pass scope. This begins the pass with the given properties and
     * automatically ends the pass as the instance is {@link #close() closed}.
     * <p>
     * Intended use is with <i>try-with-resources</i>.
     *
     * @param commandBuffer command buffer for executing the pass
     * @param renderPass    render pass to scope
     * @param framebuffer   framebuffer to use
     * @param renderArea    render area to use
     * @param clearValues   clear values for the attachments
     */
    RenderPassScope(
            final VkCommandBuffer commandBuffer,
            final RenderPass renderPass,
            final long framebuffer,
            final VkRect2D renderArea,
            final VkClearValue.Buffer clearValues
    ) {
        this.commandBuffer = commandBuffer;

        try (var stack = stackPush()) {
            final var renderPassInfo = VkRenderPassBeginInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(renderPass.getHandle())
                    .framebuffer(framebuffer)
                    .pClearValues(clearValues)
                    .renderArea(renderArea);
            vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
        }
    }

    @Override
    public void close() {
        vkCmdEndRenderPass(this.commandBuffer);
    }
}
