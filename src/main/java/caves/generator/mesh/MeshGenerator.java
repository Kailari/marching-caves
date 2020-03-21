package caves.generator.mesh;

import caves.generator.CaveSampleSpace;
import caves.generator.util.Vector3;

import java.util.ArrayDeque;
import java.util.Collection;

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
     * @param outVertices  vertices of the mesh
     * @param outNormals   vertex normals of the mesh
     * @param outIndices   indices of the mesh
     * @param surfaceLevel surface level, any sample density below this is considered empty space
     * @param startX       the X-index where to begin the flood-fill
     * @param startY       the Y-index where to begin the flood-fill
     * @param startZ       the Z-index where to begin the flood-fill
     */
    public void generate(
            final Collection<Vector3> outVertices,
            final Collection<Vector3> outNormals,
            final Collection<Integer> outIndices,
            final float surfaceLevel,
            final int startX,
            final int startY,
            final int startZ
    ) {
        System.out.printf("Marching through the sample space using flood fill. Using surface level of %.3f\n",
                          surfaceLevel);

        var allSolid = true;
        for (final var offset : MarchingCubesTables.VERTEX_OFFSETS) {
            if (this.sampleSpace.getDensity(startX + offset[X],
                                            startY + offset[Y],
                                            startZ + offset[Z]) < surfaceLevel) {
                allSolid = false;
                break;
            }
        }
        if (allSolid) {
            System.err.printf("\t-> ERROR: The starting cube at (%d, %d, %d) was fully solid!\n",
                              startX,
                              startY,
                              startZ);
            return;
        }

        System.out.printf("\t-> Starting flood fill at (%d, %d, %d)\n",
                          startX,
                          startY,
                          startZ);

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
        System.out.printf("\t-> Flood-filling the cave finished in %d steps (Naive iteration requires %d steps)\n",
                          iterations,
                          bruteIterations);
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
