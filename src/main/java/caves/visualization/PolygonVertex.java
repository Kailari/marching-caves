package caves.visualization;

import caves.visualization.rendering.VertexFormat;
import org.joml.Vector3f;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;

public class PolygonVertex {
    /** The vertex format. */
    public static final VertexFormat<PolygonVertex> FORMAT = VertexFormat
            .<PolygonVertex>builder()
            .writer((buffer, vertex) -> {
                buffer.putFloat(vertex.pos.x());
                buffer.putFloat(vertex.pos.y());
                buffer.putFloat(vertex.pos.z());

                buffer.putFloat(vertex.normal.x());
                buffer.putFloat(vertex.normal.y());
                buffer.putFloat(vertex.normal.z());

                buffer.putFloat(vertex.color.x());
                buffer.putFloat(vertex.color.y());
                buffer.putFloat(vertex.color.z());
            })
            .attribute(0, VK_FORMAT_R32G32B32_SFLOAT)
            .attribute(1, VK_FORMAT_R32G32B32_SFLOAT)
            .attribute(2, VK_FORMAT_R32G32B32_SFLOAT)
            .build();

    private final Vector3f pos;
    private final Vector3f normal;
    private final Vector3f color;

    /**
     * Constructs a new vector with a position and a color.
     *
     * @param pos    position
     * @param color  color
     * @param normal normal
     */
    public PolygonVertex(final Vector3f pos, final Vector3f normal, final Vector3f color) {
        this.pos = pos;
        this.normal = normal;
        this.color = color;
    }
}
