package caves.visualization;

import org.joml.Vector3f;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_VERTEX_INPUT_RATE_VERTEX;

public class Vertex {
    /**
     * Size of a single vertex, in bytes.
     *
     * <ul>
     *     <li><code>3</code> floats for position</li>
     *     <li><code>3</code> floats for color</li>
     * </ul>
     */
    public static final int SIZE_IN_BYTES = 3 * Float.BYTES + 3 * Float.BYTES;

    /**
     * Vertex input binding description for constructing vertex input info.
     */
    public static final VkVertexInputBindingDescription BINDING_DESCRIPTION =
            VkVertexInputBindingDescription
                    .calloc()
                    .binding(0)
                    .stride(SIZE_IN_BYTES)
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

    /**
     * Vertex input attribute descriptions for constructing vertex input info.
     * <p>
     * Note that the location values must match the values used in the shaders.
     */
    public static final VkVertexInputAttributeDescription[] ATTRIBUTE_DESCRIPTIONS = {
            VkVertexInputAttributeDescription
                    .calloc()
                    .binding(0)
                    .location(0)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(0),
            VkVertexInputAttributeDescription
                    .calloc()
                    .binding(0)
                    .location(1)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(3 * Float.BYTES),
    };

    private final Vector3f pos;
    private final Vector3f color;

    /**
     * Gets the associated position vector of this vector.
     *
     * @return the position
     */
    public Vector3f getPos() {
        return this.pos;
    }

    /**
     * Gets the associated color of this vector.
     *
     * @return the color
     */
    public Vector3f getColor() {
        return this.color;
    }

    /**
     * Constructs a new vector with a position and a color.
     *
     * @param pos   position
     * @param color color
     */
    public Vertex(final Vector3f pos, final Vector3f color) {
        this.pos = pos;
        this.color = color;
    }

    /**
     * Writes the vertex to the buffer.
     *
     * @param buffer buffer to write to
     * @param vertex vertex to write
     */
    public static void write(final ByteBuffer buffer, final Vertex vertex) {
        //noinspection ConstantConditions
        assert vertex != null : "Received a null vertex!";
        buffer.putFloat(vertex.getPos().x());
        buffer.putFloat(vertex.getPos().y());
        buffer.putFloat(vertex.getPos().z());

        buffer.putFloat(vertex.getColor().x());
        buffer.putFloat(vertex.getColor().y());
        buffer.putFloat(vertex.getColor().z());
    }
}
