package caves.visualization.rendering.command;

import caves.visualization.window.DeviceContext;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class CommandPool implements AutoCloseable {
    private final VkDevice device;
    private final long handle;

    /**
     * Gets a handle to the command pool.
     *
     * @return the command pool
     */
    public long getHandle() {
        return this.handle;
    }

    /**
     * Creates new command pool.
     *
     * @param deviceContext active device context
     * @param queueFamily   the queue family to use
     */
    public CommandPool(final DeviceContext deviceContext, final int queueFamily) {
        this.device = deviceContext.getDeviceHandle();
        try (var stack = stackPush()) {
            final var poolInfo = VkCommandPoolCreateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .queueFamilyIndex(queueFamily)
                    .flags(0); // No flags

            final var pCommandPool = stack.mallocLong(1);
            final var error = vkCreateCommandPool(this.device, poolInfo, null, pCommandPool);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create command pool: "
                                                        + translateVulkanResult(error));
            }

            this.handle = pCommandPool.get(0);
        }
    }

    @Override
    public void close() {
        vkDestroyCommandPool(this.device, this.handle, null);
    }
}
