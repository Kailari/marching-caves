package caves.visualization.rendering.mesh;

import caves.visualization.rendering.SequentialGPUBuffer;
import caves.visualization.rendering.VertexFormat;
import caves.visualization.rendering.command.CommandPool;
import caves.visualization.window.DeviceContext;
import org.lwjgl.vulkan.VkCommandBuffer;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

// TODO: Use single buffer with offsets.
//        - it is sub-optimal to pack a single mesh to two separate buffers. Why? The mesh data is
//          ALWAYS used together, indices and vertices are useless without one another. It makes
//          sense to store them together, too.
//        - This requires a bit trickery on the buffer side. We need custom buffer class capable of
//          storing multiple data types with offsets.
public final class Mesh<TVertex> implements AutoCloseable {
    private final SequentialGPUBuffer<TVertex> vertexBuffer;
    @Nullable private final SequentialGPUBuffer<Integer> indexBuffer;

    private boolean isIndexed() {
        return this.indexBuffer != null;
    }

    /**
     * Creates a new mesh from given vertices. Assumes non-indexed rendering. Mesh creation involves
     * allocating buffers for vertices. Additionally, temporary staging buffer is allocated and
     * destroyed during the process; this is to allow allocating the actual buffers as device-local,
     * potentially benefiting the performance.
     *
     * @param deviceContext device the mesh will be allocated on
     * @param commandPool   command pool to use for allocating temporary transfer command buffers
     * @param format        vertex format
     * @param vertices      mesh vertices
     */
    public Mesh(
            final DeviceContext deviceContext,
            final CommandPool commandPool,
            final VertexFormat<TVertex> format,
            final TVertex[] vertices
    ) {
        this(deviceContext, commandPool, format, vertices, null);
    }

    /**
     * Creates a new mesh from given vertices and indices. Mesh creation involves allocating buffers
     * for vertices and indices. Additionally, temporary staging buffers for both are allocated and
     * destroyed during the process; this is to allow allocating the actual buffers as device-local,
     * potentially benefiting the performance.
     * <p>
     * Passing <code>null</code> for the indices is equivalent to calling {@link
     * #Mesh(DeviceContext, CommandPool, VertexFormat, TVertex[])}.
     *
     * @param deviceContext device the mesh will be allocated on
     * @param commandPool   command pool to use for allocating temporary transfer command buffers
     * @param vertexFormat  vertex format
     * @param vertices      mesh vertices
     * @param indices       mesh element indices
     */
    public Mesh(
            final DeviceContext deviceContext,
            final CommandPool commandPool,
            final VertexFormat<TVertex> vertexFormat,
            final TVertex[] vertices,
            @Nullable final Integer[] indices
    ) {
        this.vertexBuffer = new SequentialGPUBuffer<>(
                deviceContext,
                vertices.length,
                vertexFormat.getSizeInBytes(),
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                null);
        pushToBuffer(this.vertexBuffer,
                     deviceContext,
                     commandPool,
                     vertices,
                     vertexFormat.getSizeInBytes(),
                     vertexFormat::write);

        if (indices == null) {
            this.indexBuffer = null;
        } else {
            this.indexBuffer = new SequentialGPUBuffer<>(
                    deviceContext,
                    indices.length,
                    Integer.BYTES,
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    null);
            pushToBuffer(this.indexBuffer,
                         deviceContext,
                         commandPool,
                         indices,
                         Integer.BYTES,
                         ByteBuffer::putInt);
        }
    }

    private static <T> void pushToBuffer(
            final SequentialGPUBuffer<T> buffer,
            final DeviceContext deviceContext,
            final CommandPool commandPool,
            final T[] values,
            final int elementSize,
            final BiConsumer<ByteBuffer, T> writer
    ) {
        final var stagingBuffer = new SequentialGPUBuffer<>(
                deviceContext,
                values.length,
                elementSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                writer);
        stagingBuffer.pushElements(values);
        stagingBuffer.copyToAndWait(commandPool.getHandle(), deviceContext.getTransferQueue(), buffer);
        stagingBuffer.close();
    }

    @Override
    public void close() {
        this.vertexBuffer.close();
        if (this.indexBuffer != null) {
            this.indexBuffer.close();
        }
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
        }

        if (this.isIndexed()) {
            drawIndexed(commandBuffer);
        } else {
            drawNonIndexed(commandBuffer);
        }
    }

    private void drawNonIndexed(final VkCommandBuffer commandBuffer) {
        vkCmdDraw(commandBuffer,
                  this.vertexBuffer.getElementCount(),
                  1,
                  0,
                  0);
    }

    private void drawIndexed(final VkCommandBuffer commandBuffer) {
        assert this.indexBuffer != null : "The index buffer cannot be null when using indexed rendering!";

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
