package caves.generator.mesh;

import caves.generator.CavePath;
import caves.generator.ChunkCaveSampleSpace;
import caves.generator.SampleSpaceChunk;
import caves.util.math.Vector3;

import java.util.ArrayDeque;
import java.util.Collection;

import static caves.util.profiler.Profiler.PROFILER;

public class MeshGenerator {
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;
    private static final int MAX_WALL_DISTANCE = 1024;

    private final ChunkCaveSampleSpace sampleSpace;

    /**
     * Creates a new mesh generator for marching through a {@link SampleSpaceChunk} using marching
     * cubes algorithm.
     *
     * @param sampleSpace the sample space to march through
     */
    public MeshGenerator(final ChunkCaveSampleSpace sampleSpace) {
        this.sampleSpace = sampleSpace;
    }

    /**
     * Generates a mesh for the sample space. Goes through the whole sample space in flood-fill
     * manner, starting from the given position.
     *
     * @param cavePath     cave path to iterate for potential starting positions
     * @param outVertices  vertices of the mesh
     * @param outNormals   vertex normals of the mesh
     * @param outIndices   indices of the mesh
     * @param surfaceLevel surface level, any sample density below this is considered empty space
     */
    public void generate(
            final CavePath cavePath,
            final Collection<Vector3> outVertices,
            final Collection<Vector3> outNormals,
            final Collection<Integer> outIndices,
            final float surfaceLevel
    ) {
        PROFILER.log("-> Using surface level of {}",
                     String.format("%.3f", surfaceLevel));

        var startFound = false;
        final var startNode = cavePath.get(0);
        final var caveStartSampleCoord = startNode.mul(this.sampleSpace.getSamplesPerUnit());

        PROFILER.log("-> Searching starting sample from near: ({}, {}, {})",
                     caveStartSampleCoord.x,
                     caveStartSampleCoord.y,
                     caveStartSampleCoord.z);

        int startX = (int) caveStartSampleCoord.getX();
        int startY = (int) caveStartSampleCoord.getY();
        int startZ = (int) caveStartSampleCoord.getZ();
        // Move on the x axis until we find a wall
        for (var x = 0; startX + x < MAX_WALL_DISTANCE; ++x) {
            for (var y = 0; startY + y < MAX_WALL_DISTANCE; ++y) {
                for (var z = 0; startZ + z < MAX_WALL_DISTANCE; ++z) {
                    var solidFound = false;
                    var nonSolidFound = false;
                    for (final var offset : MarchingCubesTables.VERTEX_OFFSETS) {
                        final var density = this.sampleSpace.getDensity(startX + offset[X] + x,
                                                                        startY + offset[Y] + y,
                                                                        startZ + offset[Z] + z);
                        if (density < surfaceLevel) {
                            nonSolidFound = true;
                        } else {
                            solidFound = true;
                        }

                        if (nonSolidFound && solidFound) {
                            startX += x;
                            startY += y;
                            startZ += z;
                            startFound = true;
                            break;
                        }
                    }

                    if (startFound) {
                        break;
                    }
                }
            }
        }
        if (!startFound) {
            PROFILER.err("-> ERROR: Could not find non-solid starting sample!");
            return;
        }

        PROFILER.log("-> Starting flood fill at {}",
                     String.format("(%d, %d, %d)", startX, startY, startZ));

        final var startFacings = MarchingCubes.appendToMesh(this.sampleSpace,
                                                            startX, startY, startZ,
                                                            surfaceLevel);

        final var fifoFacingQueue = new ArrayDeque<FloodFillEntry>();
        for (final var facing : startFacings) {
            final var x = startX + facing.getX();
            final var y = startY + facing.getY();
            final var z = startZ + facing.getZ();
            fifoFacingQueue.add(new FloodFillEntry(x, y, z));
        }

        var iterations = 0;
        while (!fifoFacingQueue.isEmpty()) {
            ++iterations;
            final var entry = fifoFacingQueue.pop();

            final var freeFacings = MarchingCubes.appendToMesh(this.sampleSpace,
                                                               entry.x, entry.y, entry.z,
                                                               surfaceLevel);
            for (final var facing : freeFacings) {
                final var x = entry.x + facing.getX();
                final var y = entry.y + facing.getY();
                final var z = entry.z + facing.getZ();

                if (this.sampleSpace.markQueued(x, y, z)) {
                    fifoFacingQueue.add(new FloodFillEntry(x, y, z));
                }
            }
        }

        PROFILER.log("-> Flood-filling the cave finished in {} steps ", iterations);

        PROFILER.start("Iterating through chunks and joining the vertex data");
        var skipCount = 0;
        var totalSkipped = 0;
        var totalVertexCount = 0;
        var maxVertexCount = 0;
        var totalChunksWithVerts = 0;
        for (final var chunk : this.sampleSpace.getChunks()) {
            final var vertices = chunk.getVertices();
            final var normals = chunk.getNormals();
            final var indices = chunk.getIndices();
            if (vertices == null || indices == null || normals == null) {
                ++skipCount;
                ++totalSkipped;
                continue;
            }

            if (skipCount > 0) {
                PROFILER.log("-> Skipped {} empty chunk{}",
                             skipCount,
                             skipCount > 1 ? "s" : "");
            }

            assert vertices.size() == normals.size() && vertices.size() == indices.size()
                    : "There should be equal number of vertices, normals and indices within a chunk!";

            skipCount = 0;
            totalVertexCount += vertices.size();
            maxVertexCount = Math.max(maxVertexCount, vertices.size());
            ++totalChunksWithVerts;

            final var baseIndex = outVertices.size();
            PROFILER.log("-> Adding {} vertices, starting at index {}",
                         vertices.size(),
                         baseIndex);

            outVertices.addAll(vertices);
            outNormals.addAll(normals);
            indices.forEach(index -> outIndices.add(baseIndex + index));
        }

        final var averageVertexCount = totalVertexCount / (double) totalChunksWithVerts;
        PROFILER.log("-> There are {} vertices per chunk (average in chunks with vertices)",
                     String.format("%.2f", averageVertexCount));
        PROFILER.log("-> The maximum per-chunk vertex count is {}", maxVertexCount);
        PROFILER.log("-> There were {} empty chunks", totalSkipped);
        PROFILER.end();
    }

    private static final class FloodFillEntry {
        private final int x;
        private final int y;
        private final int z;

        private FloodFillEntry(final int x, final int y, final int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
