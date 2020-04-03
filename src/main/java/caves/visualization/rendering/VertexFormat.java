package caves.visualization.rendering;

import caves.util.collections.GrowingAddOnlyList;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.function.BiConsumer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Describes format of a vertex. This information is then used by {@link
 * caves.visualization.rendering.swapchain.GraphicsPipeline GraphicsPipeline} to tell the GPU how to
 * interpret the vertex data when shaders read it.
 *
 * @param <TVertex> type of the vertex
 */
public final class VertexFormat<TVertex> {
    private final VkVertexInputBindingDescription[] bindings;
    private final VkVertexInputAttributeDescription[] attributes;
    private final BiConsumer<ByteBuffer, TVertex> writer;
    private final int sizeInBytes;

    public VkVertexInputAttributeDescription[] getAttributeDescriptions() {
        return this.attributes;
    }

    public VkVertexInputBindingDescription[] getBindingDescriptions() {
        return this.bindings;
    }

    public int getSizeInBytes() {
        return this.sizeInBytes;
    }

    private VertexFormat(
            final BiConsumer<ByteBuffer, TVertex> writer,
            final VkVertexInputBindingDescription[] bindings,
            final VkVertexInputAttributeDescription[] attributes,
            final int sizeInBytes
    ) {
        this.writer = writer;
        this.bindings = bindings;
        this.attributes = attributes;
        this.sizeInBytes = sizeInBytes;
    }

    public static <TVertex> Builder<TVertex> builder() {
        return new Builder<>();
    }

    public void write(final ByteBuffer buffer, final TVertex vertex) {
        this.writer.accept(buffer, vertex);
    }

    public static final class Builder<TVertex> {
        private final Collection<VkVertexInputAttributeDescription> attributes = new GrowingAddOnlyList<>(
                VkVertexInputAttributeDescription.class,
                1
        );

        private int sizeInBytes;
        @Nullable private BiConsumer<ByteBuffer, TVertex> writer;

        private static int sizeOf(final int format) {
            switch (format) {
                case VK_FORMAT_R32_SFLOAT:
                    return Float.BYTES;
                case VK_FORMAT_R32G32_SFLOAT:
                    return 2 * Float.BYTES;
                case VK_FORMAT_R32G32B32_SFLOAT:
                    return 3 * Float.BYTES;
                default:
                    throw new IllegalArgumentException("Unknown vertex format: " + format);
            }
        }

        /**
         * Adds the vertex writer for this format. The writer is used to serialize the vertices into
         * a {@link ByteBuffer} for uploading to the GPU.
         *
         * @param writer vertex writer
         *
         * @return this builder for chaining
         */
        public Builder<TVertex> writer(final BiConsumer<ByteBuffer, TVertex> writer) {
            this.writer = writer;
            return this;
        }

        /**
         * Adds a vertex attribute to the format. Each attribute must define a location and a
         * format. <i>(Both of these must match the attribute location values defined in shaders
         * used with this format)</i>
         *
         * @param location attribute location in the shader
         * @param format   vulkan vertex attribute format e.g. {@link org.lwjgl.vulkan.VK11#VK_FORMAT_R32_SFLOAT
         *                 VK_FORMAT_R32_SFLOAT}
         *
         * @return this builder for chaining
         */
        public Builder<TVertex> attribute(
                final int location,
                final int format
        ) {
            this.attributes.add(VkVertexInputAttributeDescription.calloc()
                                                                 .binding(0)
                                                                 .location(location)
                                                                 .format(format)
                                                                 .offset(this.sizeInBytes));
            this.sizeInBytes += sizeOf(format);
            return this;
        }

        /**
         * Builds the vertex format.
         *
         * @return the built vertex format
         */
        public VertexFormat<TVertex> build() {
            assert this.writer != null : "Vertex format must define a writer!";

            final var binding = VkVertexInputBindingDescription
                    .calloc()
                    .binding(0)
                    .stride(this.sizeInBytes)
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            return new VertexFormat<>(this.writer,
                                      new VkVertexInputBindingDescription[]{binding},
                                      this.attributes.toArray(VkVertexInputAttributeDescription[]::new),
                                      this.sizeInBytes);
        }
    }
}
