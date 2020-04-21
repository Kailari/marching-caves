package caves.generator.mesh;

import caves.generator.CavePath;
import caves.generator.ChunkCaveSampleSpace;
import caves.generator.SampleSpaceChunk;

import java.util.ArrayDeque;

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
     * Generates chunk meshes for the sample space. Goes through the whole sample space in
     * flood-fill manner. Start position is somewhere along the path.
     * <p>
     * Chunks are heuristically marked as "ready" at some point during the generation. Same chunk
     * may be marked more than once if heuristic makes bad guesses. Once chunk is marked ready, it
     * will be supplied to the <code>readyChunks</code> consumer.
     *
     * @param cavePath     cave path to iterate for potential starting positions
     * @param surfaceLevel surface level, any sample density below this is considered empty space
     * @param readyChunks  consumer which will be supplied with chunks that are "ready"
     */
    public void generate(
            final CavePath cavePath,
            final float surfaceLevel,
            final ReadyChunkConsumer readyChunks
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
        this.sampleSpace.compact();

        this.sampleSpace.markQueued(startX, startY, startZ);
        this.sampleSpace.popQueued(startX, startY, startZ);
        final var startFacings = MarchingCubes.appendToMesh(this.sampleSpace,
                                                            startX, startY, startZ,
                                                            surfaceLevel);

        final var fifoFacingQueue = new ArrayDeque<FloodFillEntry>();
        var maxQueueSize = 0;
        for (final var facing : startFacings) {
            final var x = startX + facing.getX();
            final var y = startY + facing.getY();
            final var z = startZ + facing.getZ();
            fifoFacingQueue.add(new FloodFillEntry(x, y, z));
            this.sampleSpace.markQueued(x, y, z);
            maxQueueSize = Math.max(maxQueueSize, fifoFacingQueue.size());
        }

        var iterations = 0;
        while (!fifoFacingQueue.isEmpty()) {
            ++iterations;
            final var entry = fifoFacingQueue.pop();

            this.sampleSpace.popQueued(entry.x, entry.y, entry.z);
            final var freeFacings = MarchingCubes.appendToMesh(this.sampleSpace,
                                                               entry.x, entry.y, entry.z,
                                                               surfaceLevel);
            for (final var facing : freeFacings) {
                final var x = entry.x + facing.getX();
                final var y = entry.y + facing.getY();
                final var z = entry.z + facing.getZ();

                if (this.sampleSpace.markQueued(x, y, z)) {
                    fifoFacingQueue.add(new FloodFillEntry(x, y, z));
                    maxQueueSize = Math.max(maxQueueSize, fifoFacingQueue.size());
                }
            }

            if (this.sampleSpace.isChunkReady(entry.x, entry.y, entry.z)) {
                readyChunks.accept(entry.x, entry.y, entry.z,
                                   this.sampleSpace.getChunkAt(entry.x, entry.y, entry.z));
            }
        }

        PROFILER.log("-> Flood-filling the cave finished in {} steps ", iterations);
        PROFILER.log("-> Maximum queue size was {}", maxQueueSize);
    }

    public interface ReadyChunkConsumer {
        void accept(int x, int y, int z, SampleSpaceChunk chunk);
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
