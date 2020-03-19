package caves.visualization.rendering.command;

import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkDevice;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class CommandBuffer implements AutoCloseable {
    private final VkCommandBuffer handle;
    private final long commandPool;

    /**
     * Gets the command buffer handle for this buffer.
     *
     * @return the buffer handle
     */
    public VkCommandBuffer getHandle() {
        return this.handle;
    }

    private CommandBuffer(final VkCommandBuffer handle, final long commandPool) {
        this.handle = handle;
        this.commandPool = commandPool;
    }

    /**
     * Helper for allocating a single command buffer.
     *
     * @param device      device to allocate on
     * @param commandPool command pool to allocate from
     *
     * @return the allocated command buffer
     */
    public static CommandBuffer allocate(
            final VkDevice device,
            final long commandPool
    ) {
        return allocate(device, 1, commandPool)[0];
    }

    /**
     * Helper for allocating one or more command buffers.
     *
     * @param device      device to allocate on
     * @param bufferCount number of buffers to allocate
     * @param commandPool command pool to allocate from
     *
     * @return array containing <code>bufferCount</code> command buffers
     */
    public static CommandBuffer[] allocate(
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
            final var buffers = new CommandBuffer[bufferCount];
            for (var i = 0; i < bufferCount; ++i) {
                buffers[i] = new CommandBuffer(new VkCommandBuffer(pBuffers.get(i), device),
                                               commandPool);
            }
            return buffers;
        }
    }

    @Override
    public void close() {
        vkFreeCommandBuffers(this.handle.getDevice(), this.commandPool, this.handle);
    }

    /**
     * Begins this command buffer.
     *
     * @param action actions to perform within the buffer
     */
    public void begin(final BufferAction action) {
        begin(0, action);
    }

    /**
     * Begins this command buffer. Overload allows passing in begin flags.
     *
     * @param flags  begin flags to use
     * @param action actions to perform within the buffer
     */
    public void begin(final int flags, final BufferAction action) {
        try (var ignored = new CommandBufferScope(this.getHandle(), flags)) {
            action.performCommands();
        }
    }

    public interface BufferAction {
        /**
         * Use this to record commands.
         */
        void performCommands();
    }
}
