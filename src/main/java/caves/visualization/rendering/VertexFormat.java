package caves.visualization.rendering;

import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.function.BiConsumer;

import static org.lwjgl.vulkan.VK10.*;

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
        private final Collection<VkVertexInputAttributeDescription> attributes = new ArrayList<>();

        private int sizeInBytes;
        private BiConsumer<ByteBuffer, TVertex> writer;

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

        public Builder<TVertex> writer(final BiConsumer<ByteBuffer, TVertex> writer) {
            this.writer = writer;
            return this;
        }

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

        public VertexFormat<TVertex> build() {
            final var binding = VkVertexInputBindingDescription
                    .calloc()
                    .binding(0)
                    .stride(this.sizeInBytes)
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            return new VertexFormat<>(Objects.requireNonNull(this.writer),
                                      new VkVertexInputBindingDescription[]{binding},
                                      this.attributes.toArray(VkVertexInputAttributeDescription[]::new),
                                      this.sizeInBytes);
        }
    }
}
