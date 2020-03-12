package caves.window.rendering;

import caves.window.DeviceContext;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.*;

public final class VertexBuffer implements AutoCloseable {
    private final VkDevice device;

    private final long bufferHandle;
    private final long bufferMemory;

    public long getBufferHandle() {
        return this.bufferHandle;
    }

    public VertexBuffer(
            final DeviceContext deviceContext,
            final GraphicsPipeline.Vertex[] vertices
    ) {
        this.device = deviceContext.getDevice();

        final var bufferSize = GraphicsPipeline.Vertex.SIZE_IN_BYTES * vertices.length;
        try (var stack = stackPush()) {
            final var bufferInfo = VkBufferCreateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(bufferSize)
                    .usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
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
                            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
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

        try (var stack = stackPush()) {
            final var vertexBuffer = stack.malloc(bufferSize);
            for (final var vertex : vertices) {
                vertexBuffer.putFloat(vertex.getPos().x());
                vertexBuffer.putFloat(vertex.getPos().y());

                vertexBuffer.putFloat(vertex.getColor().x());
                vertexBuffer.putFloat(vertex.getColor().y());
                vertexBuffer.putFloat(vertex.getColor().z());
            }
            vertexBuffer.flip();

            final var pData = stack.mallocPointer(1);
            vkMapMemory(this.device, this.bufferMemory, 0, bufferSize, 0, pData);
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
}
