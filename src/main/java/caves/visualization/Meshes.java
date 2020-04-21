package caves.visualization;

import caves.generator.CavePath;
import caves.generator.ChunkCaveSampleSpace;
import caves.util.collections.GrowingAddOnlyList;
import caves.util.math.Vector3;
import caves.visualization.rendering.command.CommandPool;
import caves.visualization.rendering.mesh.Mesh;
import caves.visualization.window.DeviceContext;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Collection;

import static caves.util.profiler.Profiler.PROFILER;

@SuppressWarnings("SameParameterValue")
final class Meshes {
    private static final Vector3f CAVE_MESH_VERTEX_COLOR = new Vector3f(0.7f, 0.3f, 0.1f);

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

    public static Collection<Mesh<PolygonVertex>> createCaveMeshes(
            final Vector3 middle,
            final ChunkCaveSampleSpace sampleSpace,
            final DeviceContext deviceContext,
            final CommandPool commandPool
    ) {
        final var meshes = new GrowingAddOnlyList<Mesh<PolygonVertex>>(sampleSpace.getChunkCount());
        var skipCount = 0;
        var totalSkipped = 0;
        var totalVertexCount = 0;
        var maxVertexCount = 0;
        var totalChunksWithVerts = 0;
        for (final var chunk : sampleSpace.getChunks()) {
            final var vertices = chunk.getVertices();
            final var normals = chunk.getNormals();
            final var indices = chunk.getIndices();

            if (vertices == null || indices == null || normals == null) {
                ++skipCount;
                ++totalSkipped;
                continue;
            }

            final var meshVertices = new PolygonVertex[vertices.size()];

            assert vertices.size() == normals.size() && vertices.size() == indices.size()
                    : "There should be equal number of vertices, normals and indices within a chunk!";

            skipCount = 0;
            totalVertexCount += vertices.size();
            maxVertexCount = Math.max(maxVertexCount, vertices.size());
            ++totalChunksWithVerts;

            final var vertexIter = vertices.iterator();
            final var normalIter = normals.iterator();
            for (int i = 0; i < vertices.size(); i++) {
                final var vertex = vertexIter.next();
                final var normal = normalIter.next();

                meshVertices[i] = new PolygonVertex(new Vector3f(vertex.getX() - middle.getX(),
                                                                 vertex.getY() - middle.getY(),
                                                                 vertex.getZ() - middle.getZ()),
                                                    new Vector3f(normal.getX(), normal.getY(), normal.getZ()),
                                                    CAVE_MESH_VERTEX_COLOR);
            }

            meshes.add(new Mesh<>(deviceContext,
                                  commandPool,
                                  PolygonVertex.FORMAT,
                                  meshVertices,
                                  indices.toArray(Integer[]::new)));
        }

        final var averageVertexCount = totalVertexCount / (double) totalChunksWithVerts;
        PROFILER.log("-> There are {} vertices per chunk (average in non-empty)",
                     String.format("%.2f", averageVertexCount));
        PROFILER.log("-> The maximum per-chunk vertex count is {}", maxVertexCount);
        PROFILER.log("-> There were {} empty chunks", totalSkipped);
        PROFILER.log("-> Created {} chunk meshes", meshes.size());

        return meshes;
    }
}
