package caves.visualization;

import caves.visualization.rendering.VertexFormat;
import org.joml.Vector3f;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32_SFLOAT;

public class PointVertex {
    /** The vertex format. */
    public static final VertexFormat<PointVertex> FORMAT = VertexFormat
            .<PointVertex>builder()
            .writer((buffer, vertex) -> {
                buffer.putFloat(vertex.getPos().x());
                buffer.putFloat(vertex.getPos().y());
                buffer.putFloat(vertex.getPos().z());

                buffer.putFloat(vertex.getDensity());
            })
            .attribute(0, VK_FORMAT_R32G32B32_SFLOAT)
            .attribute(1, VK_FORMAT_R32_SFLOAT)
            .build();

    private final Vector3f pos;
    private final float density;

    /**
     * Gets the associated position vector of this vertex.
     *
     * @return the position
     */
    public Vector3f getPos() {
        return this.pos;
    }

    /**
     * Gets the associated density of this vertex.
     *
     * @return the density
     */
    public float getDensity() {
        return this.density;
    }

    /**
     * Constructs a new vector with a position and a color.
     *
     * @param pos     position
     * @param density density
     */
    public PointVertex(final Vector3f pos, final float density) {
        this.pos = pos;
        this.density = density;
    }
}
