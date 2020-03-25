package caves.visualization.rendering;

import caves.visualization.rendering.command.CommandBuffer;
import caves.visualization.window.DeviceContext;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.BiConsumer;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.vulkan.VK10.*;

public final class SequentialGPUBuffer<T> extends GPUBuffer {
    private final int elementCount;

    @Nullable private final BiConsumer<ByteBuffer, T> memoryMapper;

    /**
     * Gets the count of elements stored in this buffer.
     *
     * @return the count of elements
     */
    public int getElementCount() {
        return this.elementCount;
    }

    /**
     * Allocates a new buffer on the GPU.
     *
     * @param deviceContext device to use
     * @param elementCount  the maximum number of elements the buffer may contain
     * @param elementSize   the size of a single element
     * @param usageFlags    usage flags
     * @param propertyFlags property flags
     * @param memoryMapper  memory mapper used for writing elements to the buffer
     */
    public SequentialGPUBuffer(
            final DeviceContext deviceContext,
            final int elementCount,
            final int elementSize,
            final int usageFlags,
            final int propertyFlags,
            @Nullable final BiConsumer<ByteBuffer, T> memoryMapper
    ) {
        super(deviceContext, elementSize * elementCount, usageFlags, propertyFlags);
        final var deviceLocal = (propertyFlags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0;
        assert deviceLocal || memoryMapper != null : "Either the buffer must be device local or the memory mapper must not be defined!";

        this.elementCount = elementCount;
        this.memoryMapper = memoryMapper;
    }

    /**
     * Pushes the given elements to the GPU memory.
     *
     * @param elements elements to push
     */
    public void pushElements(final T[] elements) {
        pushElements(Arrays.asList(elements));
    }


    /**
     * Pushes the given elements to the GPU memory.
     *
     * @param elements elements to push
     */
    public void pushElements(final Iterable<T> elements) {
        if (this.memoryMapper == null) {
            throw new IllegalStateException("Tried to push elements to GPU-only buffer!");
        }

        final var buffer = memAlloc((int) getSize());
        for (final var vertex : elements) {
            this.memoryMapper.accept(buffer, vertex);
        }
        buffer.flip();

        super.pushMemory(buffer);
    }

    /**
     * Copies contents of this buffer to the given target buffer.
     *
     * @param commandPool command pool to use for allocating the command buffer for the operation
     * @param queue       queue to issue the transfer operation on
     * @param other       the target buffer
     */
    public void copyToAndWait(
            final long commandPool,
            final VkQueue queue,
            final SequentialGPUBuffer<T> other
    ) {
        if (getSize() != other.getSize()) {
            throw new IllegalStateException("Cannot copy buffers with different sizes!");
        }

        try (var stack = stackPush()) {
            final var copyCommandBuffer = CommandBuffer.allocate(getDevice(), commandPool);
            copyCommandBuffer.begin(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT, () -> {
                // Execute the copy
                final var copyRegions = VkBufferCopy.callocStack(1, stack);
                copyRegions.get(0)
                           .srcOffset(0)
                           .dstOffset(0)
                           .size(getSize());
                vkCmdCopyBuffer(copyCommandBuffer.getHandle(),
                                this.getBufferHandle(),
                                other.getBufferHandle(),
                                copyRegions);
            });

            final var submitInfo = VkSubmitInfo
                    .callocStack()
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(copyCommandBuffer.getHandle()));
            final var submitResult = vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE);
            if (submitResult != VK_SUCCESS) {
                throw new IllegalStateException("Submitting a copy command buffer failed: "
                                                        + translateVulkanResult(submitResult));
            }

            vkQueueWaitIdle(queue);
            copyCommandBuffer.close();
        }
    }
}
