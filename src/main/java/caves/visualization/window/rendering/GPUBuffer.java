package caves.visualization.window.rendering;

import caves.visualization.window.DeviceContext;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.*;

public class GPUBuffer implements AutoCloseable {
    private final VkDevice device;

    private final long bufferHandle;
    private final long bufferMemory;
    private final long bufferSize;

    private final boolean deviceLocal;

    /**
     * Gets the device this buffer is allocated on.
     *
     * @return the owning device
     */
    protected VkDevice getDevice() {
        return device;
    }

    /**
     * Gets the native handle for this buffer.
     *
     * @return the handle to the underlying VkBuffer
     */
    public long getBufferHandle() {
        return this.bufferHandle;
    }

    /**
     * Gets the size of the buffer in bytes.
     *
     * @return the size of the buffer
     */
    public long getSize() {
        return this.bufferSize;
    }

    /**
     * Allocates a new GPU buffer.
     *
     * @param deviceContext device to allocate on
     * @param bufferSize    size of the buffer
     * @param usageFlags    usage flags
     * @param propertyFlags buffer properties
     */
    public GPUBuffer(
            final DeviceContext deviceContext,
            final long bufferSize,
            final int usageFlags,
            final int propertyFlags
    ) {
        this.device = deviceContext.getDevice();

        this.deviceLocal = (propertyFlags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0;
        this.bufferSize = bufferSize;

        this.bufferHandle = createBuffer(this.device, bufferSize, usageFlags);
        this.bufferMemory = allocateBufferMemory(this.bufferHandle, deviceContext, propertyFlags);
    }

    private static long createBuffer(
            final VkDevice device,
            final long bufferSize,
            final int usageFlags
    ) {
        try (var stack = stackPush()) {
            final var bufferInfo = VkBufferCreateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(bufferSize)
                    .usage(usageFlags)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            final var pBuffer = stack.mallocLong(1);
            final var error = vkCreateBuffer(device, bufferInfo, null, pBuffer);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Creating GPU buffer failed!");
            }
            return pBuffer.get(0);
        }
    }

    private static long allocateBufferMemory(
            final long bufferHandle,
            final DeviceContext deviceContext,
            final int propertyFlags
    ) {
        final var device = deviceContext.getDevice();
        try (var stack = stackPush()) {
            final var memoryRequirements = VkMemoryRequirements.callocStack(stack);
            vkGetBufferMemoryRequirements(device, bufferHandle, memoryRequirements);

            final var memoryType = deviceContext
                    .findSuitableMemoryType(memoryRequirements.memoryTypeBits(),
                                            propertyFlags)
                    .orElseThrow(() -> new IllegalStateException("Could not find suitable memory type!"));

            final var allocInfo = VkMemoryAllocateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memoryRequirements.size())
                    .memoryTypeIndex(memoryType);

            final var pBufferMemory = stack.mallocLong(1);
            final var error = vkAllocateMemory(device, allocInfo, null, pBufferMemory);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Could not allocate memory for a GPU buffer!");
            }
            final var bufferMemory = pBufferMemory.get(0);
            vkBindBufferMemory(device, bufferHandle, bufferMemory, 0);
            return bufferMemory;
        }
    }

    /**
     * Pushes the given buffer to the GPU memory. The buffer is read starting from its current
     * position and until its limit.
     *
     * @param buffer the data to push
     */
    public void pushMemory(final ByteBuffer buffer) {
        if (this.deviceLocal) {
            throw new IllegalStateException("Tried to push elements to GPU-only buffer!");
        }

        final long data;
        try (var stack = stackPush()) {
            final var pData = stack.mallocPointer(1);
            vkMapMemory(this.device, this.bufferMemory, 0, this.bufferSize, 0, pData);
            data = pData.get();
        }

        memCopy(memAddress(buffer), data, buffer.remaining());

        vkUnmapMemory(this.device, this.bufferMemory);
    }

    /**
     * If subclasses override this, be sure to call <code>super.close();</code> or else the buffer
     * handles and GPU memory allocations will leak!
     */
    @Override
    public void close() {
        vkDestroyBuffer(this.device, this.bufferHandle, null);
        vkFreeMemory(this.device, this.bufferMemory, null);
    }
}
