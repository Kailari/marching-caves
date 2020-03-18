package caves.visualization.rendering.uniform;

import caves.visualization.window.DeviceContext;
import caves.visualization.rendering.swapchain.RecreatedWithSwapChain;
import caves.visualization.rendering.swapchain.SwapChain;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDevice;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class DescriptorPool implements RecreatedWithSwapChain {
    private final VkDevice device;
    private final SwapChain swapChain;

    private long descriptorPool;
    private boolean cleanedUp;

    /**
     * Gets a handle to the descriptor pool.
     *
     * @return the descriptor pool
     */
    public long getHandle() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried to get handle for cleared descriptor pool!");
        }
        return this.descriptorPool;
    }

    /**
     * Creates new descriptor pool.
     *
     * @param deviceContext active device context
     * @param swapChain     the swapchain which images will be used
     */
    public DescriptorPool(
            final DeviceContext deviceContext,
            final SwapChain swapChain
    ) {
        this.device = deviceContext.getDeviceHandle();
        this.swapChain = swapChain;

        this.cleanedUp = true;

        recreate();
    }

    @Override
    public void recreate() {
        if (!this.cleanedUp) {
            throw new IllegalStateException("Tried to recreate descriptor pool without"
                                                    + " clearing it first!");
        }

        try (var stack = stackPush()) {
            final var poolSizes = VkDescriptorPoolSize.callocStack(1, stack);
            poolSizes.get(0)
                     .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                     .descriptorCount(swapChain.getImageCount());

            final var poolInfo = VkDescriptorPoolCreateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .pPoolSizes(poolSizes)
                    .maxSets(swapChain.getImageCount());

            final var pDescriptorPool = stack.mallocLong(1);
            final var error = vkCreateDescriptorPool(this.device, poolInfo, null, pDescriptorPool);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create descriptor pool: "
                                                        + translateVulkanResult(error));
            }

            this.descriptorPool = pDescriptorPool.get(0);
        }
        this.cleanedUp = false;
    }

    @Override
    public void cleanup() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried to cleanup already cleared descriptor pool!");
        }
        vkDestroyDescriptorPool(this.device, this.descriptorPool, null);
        this.cleanedUp = true;
    }
}
