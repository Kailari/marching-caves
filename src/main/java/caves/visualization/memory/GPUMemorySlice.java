package caves.visualization.memory;

import java.nio.ByteBuffer;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.*;

public final class GPUMemorySlice implements AutoCloseable {
    private final GPUAllocation allocation;
    private final long offset;
    private final long size;

    /**
     * Gets the memory offset for this slice.
     *
     * @return the offset
     */
    public long getOffset() {
        return this.offset;
    }

    /**
     * Gets the size of this slice.
     *
     * @return the size
     */
    public long getSize() {
        return this.size;
    }

    /**
     * Creates a new slice, pointing at the given allocation.
     *
     * @param allocation allocation this slice is from
     * @param offset     offset from the beginning of the allocation
     * @param size       size in bytes
     */
    GPUMemorySlice(final GPUAllocation allocation, final long offset, final long size) {
        this.allocation = allocation;
        this.offset = offset;
        this.size = size;
    }

    /**
     * Checks if this slice overlaps with the given memory region.
     *
     * @param start start of the region
     * @param end   end of the region
     *
     * @return <code>true</code> if this slice overlaps with the given region
     */
    boolean overlaps(final long start, final long end) {
        return start < this.offset + this.size && end > this.offset;
    }

    /**
     * Binds the buffer to this memory slice. The buffer must not be bound to any other memory
     * allocations.
     *
     * @param handle the buffer handle
     */
    public void bindBuffer(final long handle) {
        final var result = vkBindBufferMemory(this.allocation.getDevice(),
                                              handle,
                                              this.allocation.getHandle(),
                                              this.offset);
        if (result != VK_SUCCESS) {
            throw new IllegalStateException("Binding buffer memory failed: " + translateVulkanResult(result));
        }
    }

    /**
     * Binds the image to this memory slice. The image must not be bound to any other memory
     * allocations.
     *
     * @param handle the buffer handle
     */
    public void bindImage(final long handle) {
        final var result = vkBindImageMemory(this.allocation.getDevice(),
                                             handle,
                                             this.allocation.getHandle(),
                                             0);
        if (result != VK_SUCCESS) {
            throw new IllegalStateException("Binding image memory failed: " + translateVulkanResult(result));
        }
    }

    /**
     * Pushes some memory onto this slice.
     *
     * @param buffer     buffer which contents to push
     * @param bufferSize size of the buffer
     */
    public void push(final ByteBuffer buffer, final long bufferSize) {
        final long data;
        try (var stack = stackPush()) {
            final var pData = stack.mallocPointer(1);
            vkMapMemory(this.allocation.getDevice(),
                        this.allocation.getHandle(),
                        this.offset,
                        bufferSize,
                        0,
                        pData);
            data = pData.get();
        }

        memCopy(memAddress(buffer), data, buffer.remaining());

        vkUnmapMemory(this.allocation.getDevice(), this.allocation.getHandle());
    }

    @Override
    public void close() {
        this.allocation.releaseSlice(this);
    }
}
