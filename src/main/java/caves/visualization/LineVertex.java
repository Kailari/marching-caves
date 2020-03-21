package caves.visualization;

import caves.visualization.rendering.VertexFormat;
import org.joml.Vector3f;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;

public class LineVertex {
    /** The vertex format. */
    public static final VertexFormat<LineVertex> FORMAT = VertexFormat
            .<LineVertex>builder()
            .writer((buffer, vertex) -> {
                buffer.putFloat(vertex.getPos().x());
                buffer.putFloat(vertex.getPos().y());
                buffer.putFloat(vertex.getPos().z());
            })
            .attribute(0, VK_FORMAT_R32G32B32_SFLOAT)
            .build();

    private final Vector3f pos;

    /**
     * Gets the associated position vector of this vector.
     *
     * @return the position
     */
    public Vector3f getPos() {
        return this.pos;
    }

    /**
     * Constructs a new vector with a position and a color.
     *
     * @param pos position
     */
    public LineVertex(final Vector3f pos) {
        this.pos = pos;
    }
}
