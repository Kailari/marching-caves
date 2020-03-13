package caves.visualization.window.rendering;

import caves.visualization.window.DeviceContext;
import org.lwjgl.vulkan.*;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.BiConsumer;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.*;

public final class GPUBuffer<T> implements AutoCloseable {
    private final VkDevice device;

    private final long bufferHandle;
    private final long bufferMemory;
    private final int bufferSize;
    private final int elementCount;

    @Nullable private final BiConsumer<ByteBuffer, T> memoryMapper;

    private final boolean deviceLocal;

    /**
     * Gets the native handle for this buffer.
     *
     * @return the handle to the underlying VkBuffer
     */
    public long getBufferHandle() {
        return this.bufferHandle;
    }

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
     * @param usageFlags    usage flags
     * @param propertyFlags property flags
     * @param memoryMapper  memory mapper used for writing elements to the buffer
     */
    public GPUBuffer(
            final DeviceContext deviceContext,
            final int elementCount,
            final int elementSize,
            final int usageFlags,
            final int propertyFlags,
            @Nullable final BiConsumer<ByteBuffer, T> memoryMapper
    ) {
        this.device = deviceContext.getDevice();
        this.deviceLocal = (propertyFlags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0;
        if (!this.deviceLocal && memoryMapper == null) {
            System.err.println("Memory mapper was null on non-device-local GPU buffer!");
        } else if (this.deviceLocal && memoryMapper != null) {
            System.err.println("Memory mapper was defined for device-local GPU buffer!");
        }

        this.elementCount = elementCount;
        this.bufferSize = elementSize * elementCount;
        this.memoryMapper = memoryMapper;
        try (var stack = stackPush()) {
            final var bufferInfo = VkBufferCreateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(this.bufferSize)
                    .usage(usageFlags)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            final var pBuffer = stack.mallocLong(1);
            final var error = vkCreateBuffer(this.device, bufferInfo, null, pBuffer);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Creating GPU buffer failed!");
            }
            this.bufferHandle = pBuffer.get(0);
        }

        try (var stack = stackPush()) {
            final var memoryRequirements = VkMemoryRequirements.callocStack(stack);
            vkGetBufferMemoryRequirements(this.device, this.bufferHandle, memoryRequirements);

            final var memoryType = deviceContext
                    .findSuitableMemoryType(
                            memoryRequirements.memoryTypeBits(),
                            propertyFlags)
                    .orElseThrow(() -> new IllegalStateException("Could not find suitable memory type!"));

            final var allocInfo = VkMemoryAllocateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memoryRequirements.size())
                    .memoryTypeIndex(memoryType);

            final var pBufferMemory = stack.mallocLong(1);
            final var error = vkAllocateMemory(this.device, allocInfo, null, pBufferMemory);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Could not allocate memory for a GPU buffer!");
            }
            this.bufferMemory = pBufferMemory.get(0);
            vkBindBufferMemory(device, this.bufferHandle, this.bufferMemory, 0);
        }
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
        if (this.deviceLocal) {
            throw new IllegalStateException("Tried to push elements to GPU-only buffer!");
        }

        assert this.memoryMapper != null;
        try (var stack = stackPush()) {
            final var vertexBuffer = stack.malloc(this.bufferSize);
            for (final var vertex : elements) {
                this.memoryMapper.accept(vertexBuffer, vertex);
            }
            vertexBuffer.flip();

            final var pData = stack.mallocPointer(1);
            vkMapMemory(this.device, this.bufferMemory, 0, this.bufferSize, 0, pData);
            final var data = pData.get();

            memCopy(memAddress(vertexBuffer), data, vertexBuffer.remaining());

            vkUnmapMemory(this.device, this.bufferMemory);
        }
    }

    @Override
    public void close() {
        vkDestroyBuffer(this.device, this.bufferHandle, null);
        vkFreeMemory(this.device, this.bufferMemory, null);
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
            final GPUBuffer<T> other
    ) {
        if (this.bufferSize != other.bufferSize) {
            throw new IllegalStateException("Cannot copy buffers with different sizes!");
        }

        try (var stack = stackPush()) {
            final var allocInfo = VkCommandBufferAllocateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandPool(commandPool)
                    .commandBufferCount(1);

            final var pCmdBuffer = stack.mallocPointer(1);
            vkAllocateCommandBuffers(this.device, allocInfo, pCmdBuffer);

            final var copyCommandBuffer = new VkCommandBuffer(pCmdBuffer.get(0), this.device);

            final var beginInfo = VkCommandBufferBeginInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            var error = vkBeginCommandBuffer(copyCommandBuffer, beginInfo);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Beginning a copy command buffer failed: "
                                                        + translateVulkanResult(error));
            }

            // Execute the copy
            final var copyRegions = VkBufferCopy.callocStack(1, stack);
            copyRegions.get(0)
                       .srcOffset(0)
                       .dstOffset(0)
                       .size(this.bufferSize);
            vkCmdCopyBuffer(copyCommandBuffer, this.bufferHandle, other.bufferHandle, copyRegions);

            error = vkEndCommandBuffer(copyCommandBuffer);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Finalizing a copy command buffer failed: "
                                                        + translateVulkanResult(error));
            }

            final var submitInfo = VkSubmitInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(pCmdBuffer);
            error = vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Submitting a copy command buffer failed: "
                                                        + translateVulkanResult(error));
            }

            vkQueueWaitIdle(queue);
            vkFreeCommandBuffers(this.device, commandPool, copyCommandBuffer);
        }
    }
}
