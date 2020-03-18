package caves.visualization.rendering.mesh;

import caves.visualization.Vertex;
import caves.visualization.rendering.CommandPool;
import caves.visualization.rendering.SequentialGPUBuffer;
import caves.visualization.window.DeviceContext;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class Mesh implements AutoCloseable {
    private final SequentialGPUBuffer<Vertex> vertexBuffer;
    private final SequentialGPUBuffer<Integer> indexBuffer;

    /**
     * Creates a new mesh from given vertices and indices. Mesh creation involves allocating buffers
     * for vertices and indices. Additionally, temporary staging buffers for both are allocated and
     * destroyed during the process; this is to allow allocating the actual buffers as device-local,
     * potentially benefiting the performance.
     *
     * @param deviceContext device the mesh will be allocated on
     * @param commandPool   command pool to use for allocating temporary transfer command buffers
     * @param vertices      mesh vertices
     * @param indices       mesh element indices
     */
    public Mesh(
            final DeviceContext deviceContext,
            final CommandPool commandPool,
            final Vertex[] vertices,
            final Integer[] indices
    ) {
        this.vertexBuffer = new SequentialGPUBuffer<>(
                deviceContext,
                vertices.length,
                Vertex.SIZE_IN_BYTES,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                null);
        this.indexBuffer = new SequentialGPUBuffer<>(
                deviceContext,
                indices.length,
                Integer.BYTES,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                null);

        updateVertexBuffer(this.vertexBuffer, deviceContext, commandPool, vertices);
        updateIndexBuffer(this.indexBuffer, deviceContext, commandPool, indices);
    }

    private static void updateVertexBuffer(
            final SequentialGPUBuffer<Vertex> vertexBuffer,
            final DeviceContext deviceContext,
            final CommandPool commandPool,
            final Vertex[] vertices
    ) {
        final var stagingBuffer = new SequentialGPUBuffer<>(
                deviceContext,
                vertices.length,
                Vertex.SIZE_IN_BYTES,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                Vertex::write);

        stagingBuffer.pushElements(Arrays.asList(vertices));
        stagingBuffer.copyToAndWait(commandPool.getHandle(),
                                    deviceContext.getTransferQueue(),
                                    vertexBuffer);
        stagingBuffer.close();
    }

    private static void updateIndexBuffer(
            final SequentialGPUBuffer<Integer> indexBuffer,
            final DeviceContext deviceContext,
            final CommandPool commandPool,
            final Integer[] indices
    ) {
        final var stagingBuffer = new SequentialGPUBuffer<Integer>(
                deviceContext,
                indices.length,
                Integer.BYTES,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                ByteBuffer::putInt);
        stagingBuffer.pushElements(indices);
        stagingBuffer.copyToAndWait(commandPool.getHandle(),
                                    deviceContext.getTransferQueue(),
                                    indexBuffer);
        stagingBuffer.close();
    }

    @Override
    public void close() {
        this.vertexBuffer.close();
        this.indexBuffer.close();
    }

    /**
     * Records a draw command to the given command buffer.
     *
     * @param commandBuffer command buffer to record the command to
     */
    public void draw(final VkCommandBuffer commandBuffer) {
        try (var stack = stackPush()) {
            vkCmdBindVertexBuffers(commandBuffer,
                                   0,
                                   stack.longs(this.vertexBuffer.getBufferHandle()),
                                   stack.longs(0L));
            vkCmdBindIndexBuffer(commandBuffer,
                                 this.indexBuffer.getBufferHandle(),
                                 0,
                                 VK_INDEX_TYPE_UINT32);
            vkCmdDrawIndexed(commandBuffer,
                             this.indexBuffer.getElementCount(),
                             1,
                             0,
                             0,
                             0);
        }
    }
}
