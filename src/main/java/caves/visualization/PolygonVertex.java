package caves.visualization;

import caves.visualization.rendering.VertexFormat;
import org.joml.Vector3f;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;

public class PolygonVertex {
    /** The vertex format. */
    public static final VertexFormat<PolygonVertex> FORMAT = VertexFormat
            .<PolygonVertex>builder()
            .writer((buffer, vertex) -> {
                buffer.putFloat(vertex.getPos().x());
                buffer.putFloat(vertex.getPos().y());
                buffer.putFloat(vertex.getPos().z());

                buffer.putFloat(vertex.getColor().x());
                buffer.putFloat(vertex.getColor().y());
                buffer.putFloat(vertex.getColor().z());
            })
            .attribute(0, VK_FORMAT_R32G32B32_SFLOAT)
            .attribute(1, VK_FORMAT_R32G32B32_SFLOAT)
            .build();

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
    public PolygonVertex(final Vector3f pos, final Vector3f color) {
        this.pos = pos;
        this.color = color;
    }
}
