package caves.visualization.memory;

import java.nio.ByteBuffer;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.*;

public class GPUMemorySlice implements AutoCloseable {
    private final GPUAllocation allocation;
    private final long offset;
    private final long size;

    public long getOffset() {
        return this.offset;
    }

    public long getSize() {
        return this.size;
    }

    public GPUMemorySlice(final GPUAllocation allocation, final long offset, final long size) {
        this.allocation = allocation;
        this.offset = offset;
        this.size = size;
    }

    public boolean overlaps(final long a0, final long b0) {
        final var a1 = this.offset;
        final var b1 = a1 + this.size;
        return a0 < b1 && b0 > a1;
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
