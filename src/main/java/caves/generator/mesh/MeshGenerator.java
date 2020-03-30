package caves.generator.mesh;

import caves.generator.CavePath;
import caves.generator.CaveSampleSpace;
import caves.util.math.Vector3;

import java.util.ArrayDeque;
import java.util.Collection;

import static caves.util.profiler.Profiler.PROFILER;

public class MeshGenerator {
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    private final CaveSampleSpace sampleSpace;

    /**
     * Creates a new mesh generator for marching through a {@link CaveSampleSpace} using marching
     * cubes algorithm.
     *
     * @param sampleSpace the sample space to march through
     */
    public MeshGenerator(final CaveSampleSpace sampleSpace) {
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
        int startX = -1;
        int startY = -1;
        int startZ = -1;
        for (final var node : cavePath.getAllNodes()) {
            final var caveStartSampleCoord = node.sub(this.sampleSpace.getMin(), new Vector3())
                                                 .abs()
                                                 .mul(this.sampleSpace.getSamplesPerUnit());

            startX = (int) caveStartSampleCoord.getX();
            startY = (int) caveStartSampleCoord.getY();
            startZ = (int) caveStartSampleCoord.getZ();
            for (final var offset : MarchingCubesTables.VERTEX_OFFSETS) {
                if (this.sampleSpace.getDensity(startX + offset[X],
                                                startY + offset[Y],
                                                startZ + offset[Z]) < surfaceLevel) {
                    startFound = true;
                    break;
                }
            }

            if (startFound) {
                break;
            }
        }
        if (!startFound) {
            PROFILER.err("-> ERROR: Could not find non-solid starting sample!");
            return;
        }

        PROFILER.log("-> Starting flood fill at {}",
                     String.format("(%d, %d, %d)", startX, startY, startZ));

        final var startFacings = MarchingCubes.appendToMesh(outVertices, outNormals, outIndices,
                                                            this.sampleSpace,
                                                            startX, startY, startZ,
                                                            surfaceLevel);

        final var alreadyQueued = new boolean[this.sampleSpace.getTotalCount()];
        alreadyQueued[this.sampleSpace.getSampleIndex(startX, startY, startZ)] = true;

        final var fifoFacingQueue = new ArrayDeque<FloodFillEntry>();
        for (final var facing : startFacings) {
            final var x = startX + facing.getX();
            final var y = startY + facing.getY();
            final var z = startZ + facing.getZ();
            final var index = this.sampleSpace.getSampleIndex(x, y, z);
            fifoFacingQueue.add(new FloodFillEntry(x, y, z));
            alreadyQueued[index] = true;
        }

        var iterations = 0;
        while (!fifoFacingQueue.isEmpty()) {
            ++iterations;
            final var entry = fifoFacingQueue.pop();

            final var freeFacings = MarchingCubes.appendToMesh(outVertices, outNormals, outIndices,
                                                               this.sampleSpace,
                                                               entry.x, entry.y, entry.z,
                                                               surfaceLevel);
            for (final var facing : freeFacings) {
                final var x = entry.x + facing.getX();
                final var y = entry.y + facing.getY();
                final var z = entry.z + facing.getZ();
                final var index = this.sampleSpace.getSampleIndex(x, y, z);

                final var xOOB = x < 2 || x >= this.sampleSpace.getCountX() - 2;
                final var yOOB = y < 2 || y >= this.sampleSpace.getCountY() - 2;
                final var zOOB = z < 2 || z >= this.sampleSpace.getCountZ() - 2;
                if (alreadyQueued[index] || xOOB || yOOB || zOOB) {
                    continue;
                }
                fifoFacingQueue.add(new FloodFillEntry(x, y, z));
                alreadyQueued[index] = true;
            }
        }

        final var bruteIterations = (this.sampleSpace.getCountX() - 4)
                * (this.sampleSpace.getCountY() - 4)
                * (this.sampleSpace.getCountZ() - 4);
        PROFILER.log("-> Flood-filling the cave finished in {} steps ", iterations);
        PROFILER.log("-> Naive iteration requires {} steps.", bruteIterations);
        PROFILER.log("-> We saved {} steps with flood fill", bruteIterations - iterations);
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
