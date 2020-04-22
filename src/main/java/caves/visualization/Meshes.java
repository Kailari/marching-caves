package caves.visualization;

import caves.generator.CavePath;
import caves.generator.SampleSpaceChunk;
import caves.util.collections.VertexArray;
import caves.util.math.Vector3;
import caves.visualization.rendering.command.CommandPool;
import caves.visualization.rendering.mesh.Mesh;
import caves.visualization.window.DeviceContext;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.stream.IntStream;

@SuppressWarnings("SameParameterValue")
final class Meshes {
    private static final Vector3 CAVE_MESH_VERTEX_COLOR = new Vector3(0.7f, 0.3f, 0.1f);
    private static final PolygonVertex NULL_VERTEX = new PolygonVertex(new Vector3(0, 0, 0),
                                                                       new Vector3(0, 0, 0),
                                                                       CAVE_MESH_VERTEX_COLOR);

    private Meshes() {
    }

    static Mesh<LineVertex> createLineMesh(
            final CavePath cavePath,
            final Vector3 middle,
            final DeviceContext deviceContext,
            final CommandPool commandPool
    ) {
        final var lineVertices = Arrays.stream(cavePath.getNodesOrdered())
                                       .map(pos -> new LineVertex(new Vector3f(pos.getX() - middle.getX(),
                                                                               pos.getY() - middle.getY(),
                                                                               pos.getZ() - middle.getZ())))
                                       .toArray(LineVertex[]::new);
        return new Mesh<>(deviceContext,
                          commandPool,
                          LineVertex.FORMAT,
                          lineVertices);
    }

    public static Mesh<PolygonVertex> createChunkMesh(
            final Vector3 middle,
            final DeviceContext deviceContext,
            final CommandPool commandPool,
            final VertexArray<SampleSpaceChunk.Vertex> vertices
    ) {
        final var meshVertices = new PolygonVertex[vertices.size()];
        vertices.mappingCopyTo(meshVertices, (vertex) -> createRenderVertex(middle, vertex));

        return new Mesh<>(deviceContext,
                          commandPool,
                          PolygonVertex.FORMAT,
                          meshVertices,
                          IntStream.range(0, meshVertices.length)
                                   .boxed()
                                   .toArray(Integer[]::new));
    }

    private static PolygonVertex createRenderVertex(
            final Vector3 middle,
            @Nullable final SampleSpaceChunk.Vertex vertex
    ) {
        if (vertex == null) {
            return NULL_VERTEX;
        }

        return new PolygonVertex(vertex.getPosition().sub(middle, new Vector3()),
                                 vertex.getNormal(),
                                 CAVE_MESH_VERTEX_COLOR);
    }
}
