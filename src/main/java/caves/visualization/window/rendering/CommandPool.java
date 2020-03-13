package caves.visualization.window.rendering;

import caves.visualization.window.DeviceContext;
import caves.visualization.window.rendering.swapchain.SwapChain;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDevice;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class CommandPool implements AutoCloseable {
    private final VkDevice device;
    private final long commandPool;

    /**
     * Gets a handle to the command pool.
     *
     * @return the command pool
     */
    public long getHandle() {
        return this.commandPool;
    }

    /**
     * Creates new command pool.
     *
     * @param deviceContext active device context
     * @param swapChain     the swapchain which images will be used
     */
    public CommandPool(
            final DeviceContext deviceContext,
            final SwapChain swapChain
    ) {
        this.device = deviceContext.getDevice();
        this.commandPool = createCommandPool(deviceContext);
    }

    private static long createCommandPool(final DeviceContext deviceContext) {
        try (var stack = stackPush()) {
            final var poolInfo = VkCommandPoolCreateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .queueFamilyIndex(deviceContext.getGraphicsQueueFamilyIndex())
                    .flags(0); // No flags

            final var pCommandPool = stack.mallocLong(1);
            final var error = vkCreateCommandPool(deviceContext.getDevice(), poolInfo, null, pCommandPool);
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
}
