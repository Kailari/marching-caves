package caves.window.rendering;

import caves.window.DeviceContext;
import org.lwjgl.vulkan.*;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class CommandPool implements AutoCloseable {
    private final VkDevice device;
    private final long commandPool;

    /**
     * Creates new command pool.
     *
     * @param deviceContext    active device context
     */
    public CommandPool(
            final DeviceContext deviceContext
    ) {
        this.device = deviceContext.getDevice();

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

            this.commandPool = pCommandPool.get(0);
        }
    }


    @Override
    public void close() {
        vkDestroyCommandPool(this.device, this.commandPool, null);
    }

    public long getHandle() {
        return this.commandPool;
    }
}
