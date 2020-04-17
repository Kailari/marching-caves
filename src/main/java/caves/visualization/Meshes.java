package caves.visualization;

import caves.generator.CavePath;
import caves.util.math.Vector3;
import caves.visualization.rendering.command.CommandPool;
import caves.visualization.rendering.mesh.Mesh;
import caves.visualization.window.DeviceContext;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Collection;

@SuppressWarnings("SameParameterValue")
final class Meshes {
    private static final Vector3f CAVE_MESH_VERTEX_COLOR = new Vector3f(0.7f, 0.3f, 0.1f);

    private Meshes() {
    }

    static Mesh<PolygonVertex> createCaveMesh(
            final Vector3 middle,
            final Collection<Integer> caveIndices,
            final Collection<Vector3> caveVertices,
            final Collection<Vector3> caveNormals,
            final DeviceContext deviceContext,
            final CommandPool commandPool
    ) {
        final var actualVertices = new PolygonVertex[caveVertices.size()];
        final var vertexIter = caveVertices.iterator();
        final var normalIter = caveNormals.iterator();
        for (var i = 0; i < actualVertices.length; ++i) {
            final var pos = vertexIter.next();
            final var normal = normalIter.next();
            actualVertices[i] = new PolygonVertex(new Vector3f(pos.getX() - middle.getX(),
                                                               pos.getY() - middle.getY(),
                                                               pos.getZ() - middle.getZ()),
                                                  new Vector3f(normal.getX(), normal.getY(), normal.getZ()),
                                                  CAVE_MESH_VERTEX_COLOR);
        }

        return new Mesh<>(deviceContext,
                          commandPool,
                          PolygonVertex.FORMAT,
                          actualVertices,
                          caveIndices.toArray(Integer[]::new));
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
}
