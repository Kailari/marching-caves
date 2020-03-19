package caves.visualization.rendering.command;

import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class CommandBufferScope implements AutoCloseable {
    private final VkCommandBuffer commandBuffer;

    /**
     * Begins a command buffer. This constructor is analogous to call to
     * <code>vkBeginCommandBuffer</code>
     *
     * @param commandBuffer buffer to begin
     * @param flags         begin flags
     */
    public CommandBufferScope(final VkCommandBuffer commandBuffer, final int flags) {
        this.commandBuffer = commandBuffer;

        try (var stack = stackPush()) {
            final var beginInfo = VkCommandBufferBeginInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(flags);

            final var result = vkBeginCommandBuffer(commandBuffer, beginInfo);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to begin recording a command buffer: "
                                                        + translateVulkanResult(result));
            }
        }
    }

    @Override
    public void close() {
        final var error = vkEndCommandBuffer(this.commandBuffer);
        if (error != VK_SUCCESS) {
            throw new IllegalStateException("Failed to finalize recording a command buffer: "
                                                    + translateVulkanResult(error));
        }
    }
}
