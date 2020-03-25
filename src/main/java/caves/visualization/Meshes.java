package caves.visualization;

import caves.generator.CavePath;
import caves.generator.CaveSampleSpace;
import caves.generator.mesh.MarchingCubesTables;
import caves.util.math.Vector3;
import caves.visualization.rendering.command.CommandPool;
import caves.visualization.rendering.mesh.Mesh;
import caves.visualization.window.DeviceContext;
import org.joml.Vector3f;

import java.util.*;

@SuppressWarnings("SameParameterValue")
final class Meshes {
    private static final Vector3f CAVE_MESH_VERTEX_COLOR = new Vector3f(0.7f, 0.3f, 0.1f);

    private Meshes() {
    }

    static Mesh<PolygonVertex> createCaveMesh(
            final Vector3 middle,
            final Collection<Integer> caveIndices,
            final List<Vector3> caveVertices,
            final List<Vector3> caveNormals,
            final DeviceContext deviceContext,
            final CommandPool commandPool
    ) {
        final var actualVertices = new PolygonVertex[caveVertices.size()];
        for (var i = 0; i < actualVertices.length; ++i) {
            final var pos = caveVertices.get(i);
            final var normal = caveNormals.get(i);
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

    static Mesh<PointVertex> createPointMesh(
            final float surfaceLevel,
            final CaveSampleSpace sampleSpace,
            final int startX,
            final int startY,
            final int startZ,
            final Vector3 middle,
            final DeviceContext deviceContext,
            final CommandPool commandPool
    ) {
        final var vertices = new ArrayList<PointVertex>();

        final var queue = new ArrayDeque<PointVertexEntry>();
        final var alreadyQueued = new boolean[sampleSpace.getTotalCount()];
        queue.add(new PointVertexEntry(startX, startY, startZ));

        while (!queue.isEmpty()) {
            final var entry = queue.pop();
            final var pos = sampleSpace.getPos(entry.x, entry.y, entry.z);
            final float density = sampleSpace.getDensity(entry.x, entry.y, entry.z);
            vertices.add(new PointVertex(new Vector3f(pos.getX() - middle.getX(),
                                                      pos.getY() - middle.getY(),
                                                      pos.getZ() - middle.getZ()),
                                         density));

            for (final var facing : MarchingCubesTables.Facing.values()) {
                final var x = entry.x + facing.getX();
                final var y = entry.y + facing.getY();
                final var z = entry.z + facing.getZ();
                final var index = sampleSpace.getSampleIndex(x, y, z);

                final var xOOB = x < 2 || x >= sampleSpace.getCountX() - 2;
                final var yOOB = y < 2 || y >= sampleSpace.getCountY() - 2;
                final var zOOB = z < 2 || z >= sampleSpace.getCountZ() - 2;
                if (xOOB || yOOB || zOOB
                        || alreadyQueued[index]
                        || sampleSpace.getDensity(index, x, y, z) >= surfaceLevel
                ) {
                    continue;
                }
                queue.add(new PointVertexEntry(x, y, z));
                alreadyQueued[index] = true;
            }
        }

        return new Mesh<>(deviceContext,
                          commandPool,
                          PointVertex.FORMAT,
                          vertices.toArray(PointVertex[]::new));
    }

    private static final class PointVertexEntry {
        private final int x;
        private final int y;
        private final int z;

        private PointVertexEntry(final int x, final int y, final int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
