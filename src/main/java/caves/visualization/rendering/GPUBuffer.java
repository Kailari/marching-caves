package caves.visualization.rendering;

import caves.visualization.memory.GPUMemorySlice;
import caves.visualization.window.DeviceContext;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkDevice;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class GPUBuffer implements AutoCloseable {
    private final VkDevice device;

    private final GPUMemorySlice bufferMemory;
    private final long bufferHandle;
    private final long bufferSize;

    private final boolean deviceLocal;

    /**
     * Gets the device this buffer is allocated on.
     *
     * @return the owning device
     */
    protected VkDevice getDevice() {
        return this.device;
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
        this.device = deviceContext.getDeviceHandle();

        this.deviceLocal = (propertyFlags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0;
        this.bufferSize = bufferSize;

        this.bufferHandle = createBuffer(deviceContext, bufferSize, usageFlags);
        this.bufferMemory = allocateBufferMemory(this.bufferHandle, deviceContext, propertyFlags);
    }

    private static long createBuffer(
            final DeviceContext deviceContext,
            final long bufferSize,
            final int usageFlags
    ) {
        try (var stack = stackPush()) {
            final var bufferInfo = VkBufferCreateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(bufferSize)
                    .usage(usageFlags)
                    .sharingMode(VK_SHARING_MODE_CONCURRENT)
                    .pQueueFamilyIndices(stack.ints(deviceContext.getQueueFamilies().getGraphics(),
                                                    deviceContext.getQueueFamilies().getTransfer()));

            final var pBuffer = stack.mallocLong(1);
            final var error = vkCreateBuffer(deviceContext.getDeviceHandle(),
                                             bufferInfo,
                                             null,
                                             pBuffer);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Creating GPU buffer failed!");
            }
            return pBuffer.get(0);
        }
    }

    private static GPUMemorySlice allocateBufferMemory(
            final long bufferHandle,
            final DeviceContext deviceContext,
            final int propertyFlags
    ) {
        final var slice = deviceContext.getMemoryAllocator()
                                       .allocateBufferMemory(bufferHandle, propertyFlags);
        slice.bindBuffer(bufferHandle);

        return slice;
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

        this.bufferMemory.push(buffer, this.bufferSize);
    }

    /**
     * If subclasses override this, be sure to call <code>super.close();</code> or else the buffer
     * handles and GPU memory allocations will leak!
     */
    @Override
    public void close() {
        vkDestroyBuffer(this.device, this.bufferHandle, null);
        this.bufferMemory.close();
    }
}
