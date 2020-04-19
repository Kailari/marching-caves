package caves.generator;

import caves.util.collections.ChunkMap;
import caves.util.math.Vector3;

import java.util.function.Function;

import static caves.util.math.MathUtil.fastFloor;

public class ChunkCaveSampleSpace {
    public static final int CHUNK_SIZE = 32;

    private final ChunkMap chunks = new ChunkMap(2048);
    private final Function<Vector3, Float> densityFunction;
    private final float chunkSize;
    private final float samplesPerUnit;

    private Vector3 min = new Vector3();
    private Vector3 max = new Vector3();

    public Vector3 getMin() {
        return this.min;
    }

    public Vector3 getMax() {
        return this.max;
    }

    public float getSamplesPerUnit() {
        return this.samplesPerUnit;
    }

    /**
     * Gets the number of chunks created to this sample space.
     *
     * @return the number of chunks
     */
    public int getChunkCount() {
        return this.chunks.getSize();
    }

    /**
     * Gets an iterable over all of the chunks in this sample space.
     */
    public Iterable<SampleSpaceChunk> getChunks() {
        return this.chunks.values();
    }

    public int getTotalVertices() {
        var count = 0;
        for (final var chunk : getChunks()) {
            final var vertices = chunk.getVertices();
            if (vertices != null) {
                count += vertices.size();
            }
        }

        return count;
    }

    public ChunkCaveSampleSpace(
            final float samplesPerUnit,
            final Function<Vector3, Float> densityFunction
    ) {
        this.densityFunction = densityFunction;
        this.samplesPerUnit = samplesPerUnit;

        final var spaceBetweenSamples = (1.0f / this.samplesPerUnit);
        this.chunkSize = CHUNK_SIZE * spaceBetweenSamples;
    }

    // XXX: Sneaky cast ints to longs by using long as parameter type. This avoids overflowing the integer while shifting
    private static long chunkIndex(final long x, final long y, final long z) {
        // Pack first 20 bits of each into a single 64bit sequence (yes, whopping 4 bits are wasted! :o)
        return (x & 0xF_FFFF) | ((y & 0xF_FFFF) << 20) | ((z & 0xF_FFFF) << 40);
    }

    /**
     * Gets the density for the given sample.
     *
     * @param x sample x index
     * @param y sample y index
     * @param z sample z index
     *
     * @return the density of the specified sample
     */
    public float getDensity(final int x, final int y, final int z) {
        return getChunkAt(x, y, z).getDensity(x, y, z, () -> this.densityFunction.apply(getPos(x, y, z)));
    }

    private SampleSpaceChunk createChunk(
            final int chunkX,
            final int chunkY,
            final int chunkZ
    ) {
        final var startX = chunkX * this.chunkSize;
        final var startY = chunkY * this.chunkSize;
        final var startZ = chunkZ * this.chunkSize;

        final var chunk = new SampleSpaceChunk(startX,
                                               startY,
                                               startZ,
                                               this.chunkSize);
        this.min = this.min.min(chunk.getMin(), this.min);
        this.max = this.max.max(chunk.getMax(), this.min);
        return chunk;
    }

    public Vector3 getPos(final int x, final int y, final int z) {
        final var spaceBetweenSamples = (1.0f / this.samplesPerUnit);
        return new Vector3(x * spaceBetweenSamples,
                           y * spaceBetweenSamples,
                           z * spaceBetweenSamples);
    }

    /**
     * Tries to mark the given sample as queued. If the sample is already queued, the method does
     * nothing.
     *
     * @param x the x-coordinate of the sample
     * @param y the y-coordinate of the sample
     * @param z the z-coordinate of the sample
     *
     * @return <code>true</code> if and only if the sample was not previously queued.
     */
    public boolean markQueued(final int x, final int y, final int z) {
        return getChunkAt(x, y, z).markQueued(x, y, z);
    }

    /**
     * Gets the chunk at the given sample location.
     *
     * @param x x-coordinate of the sample to get the chunk for
     * @param y y-coordinate of the sample to get the chunk for
     * @param z z-coordinate of the sample to get the chunk for
     *
     * @return the chunk at the given sample coordinates
     */
    public SampleSpaceChunk getChunkAt(final int x, final int y, final int z) {
        final var chunkX = fastFloor(x / (float) CHUNK_SIZE);
        final var chunkY = fastFloor(y / (float) CHUNK_SIZE);
        final var chunkZ = fastFloor(z / (float) CHUNK_SIZE);
        final var chunkIndex = chunkIndex(chunkX, chunkY, chunkZ);
        return this.chunks.createIfAbsent(chunkIndex, () -> this.createChunk(chunkX, chunkY, chunkZ));
    }
}
