package caves.window.rendering;

import caves.window.DeviceContext;
import org.lwjgl.vulkan.*;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.*;

public final class VertexBuffer implements AutoCloseable {
    private final VkDevice device;

    private final long bufferHandle;
    private final long bufferMemory;
    private final int bufferSize;
    private final int vertexCount;

    private final boolean deviceLocal;

    public long getBufferHandle() {
        return this.bufferHandle;
    }

    public int getVertexCount() {
        return this.vertexCount;
    }

    public VertexBuffer(
            final DeviceContext deviceContext,
            final int vertexCount,
            final int usageFlags,
            final int propertyFlags
    ) {
        this.device = deviceContext.getDevice();
        this.deviceLocal = (propertyFlags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0;

        this.vertexCount = vertexCount;
        this.bufferSize = GraphicsPipeline.Vertex.SIZE_IN_BYTES * vertexCount;
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
                throw new IllegalStateException("Creating vertex buffer failed!");
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
                throw new IllegalStateException("Could not allocate memory for a vertex buffer!");
            }
            this.bufferMemory = pBufferMemory.get(0);
            vkBindBufferMemory(device, this.bufferHandle, this.bufferMemory, 0);
        }
    }

    public void pushVertices(final GraphicsPipeline.Vertex[] vertices) {
        if (this.deviceLocal) {
            throw new IllegalStateException("Tried to push vertices to GPU-only buffer!");
        }

        try (var stack = stackPush()) {
            final var vertexBuffer = stack.malloc(this.bufferSize);
            for (final var vertex : vertices) {
                vertexBuffer.putFloat(vertex.getPos().x());
                vertexBuffer.putFloat(vertex.getPos().y());

                vertexBuffer.putFloat(vertex.getColor().x());
                vertexBuffer.putFloat(vertex.getColor().y());
                vertexBuffer.putFloat(vertex.getColor().z());
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

    public void copyToAndWait(
            final long commandPool,
            final VkQueue queue,
            final VertexBuffer other
    ) {
        if (this.bufferSize != other.bufferSize) {
            throw new IllegalStateException("Cannot copy as the buffers are of different sizes!");
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
