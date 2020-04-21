package caves.generator;

import caves.util.collections.LongMap;
import caves.util.math.Vector3;

import java.util.function.Function;

import static caves.util.math.MathUtil.fastFloor;
import static caves.util.profiler.Profiler.PROFILER;

public final class ChunkCaveSampleSpace {
    /**
     * Size of a sample chunk. Same size is used on all three axes.
     */
    public static final int CHUNK_SIZE = 32;

    private final LongMap<SampleSpaceChunk> chunks = new LongMap<>(2048);
    private final Function<Vector3, Float> densityFunction;
    private final float chunkSize;
    private final float samplesPerUnit;
    private final float surfaceLevel;

    private Vector3 min = new Vector3();
    private Vector3 max = new Vector3();

    /**
     * Gets the component-wise minimum bounding coordinate for this sample space.
     *
     * @return the min coordinate
     */
    public Vector3 getMin() {
        return this.min;
    }

    /**
     * Gets the component-wise maximum bounding coordinate for this sample space.
     *
     * @return the max coordinate
     */
    public Vector3 getMax() {
        return this.max;
    }

    /**
     * Gets the number of samples per unit in this sample space.
     *
     * @return the samples per unit
     */
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
     *
     * @return all chunks in this sample space as an iterable
     */
    public Iterable<SampleSpaceChunk> getChunks() {
        return this.chunks.values();
    }

    /**
     * Gets the total vertex count in this sample space.
     *
     * @return the total number of vertices
     */
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

    /**
     * Creates a new chunked sampling space. Sample densities are calculated using the given density
     * function.
     *
     * @param samplesPerUnit  samples per unit
     * @param densityFunction density function
     * @param surfaceLevel    isosurface solid surface density level
     */
    public ChunkCaveSampleSpace(
            final float samplesPerUnit,
            final Function<Vector3, Float> densityFunction,
            final float surfaceLevel
    ) {
        this.densityFunction = densityFunction;
        this.samplesPerUnit = samplesPerUnit;
        this.surfaceLevel = surfaceLevel;

        final var spaceBetweenSamples = (1.0f / this.samplesPerUnit);
        this.chunkSize = CHUNK_SIZE * spaceBetweenSamples;
    }

    /**
     * Calculates chunk index for the given chunk coordinates.
     * <p>
     * XXX: Sneaky cast ints to longs by using long as parameter type. This avoids overflowing the
     * integer while shifting
     *
     * @param chunkX X-coordinate of the chunk
     * @param chunkY Y-coordinate of the chunk
     * @param chunkZ Z-coordinate of the chunk
     *
     * @return packed index for the chunk
     */
    public static long chunkIndex(final long chunkX, final long chunkY, final long chunkZ) {
        // Pack first 20 bits of each into a single 64bit sequence (yes, whopping 4 bits are wasted! :o)
        return (chunkX & 0xF_FFFF) | ((chunkY & 0xF_FFFF) << 20) | ((chunkZ & 0xF_FFFF) << 40);
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
        return getChunkAt(x, y, z).getDensity(x,
                                              y,
                                              z,
                                              () -> this.densityFunction.apply(getPos(x, y, z)));
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
                                               this.chunkSize,
                                               this.surfaceLevel);
        this.min = this.min.min(chunk.getMin(), this.min);
        this.max = this.max.max(chunk.getMax(), this.min);
        return chunk;
    }

    /**
     * Gets in-world coordinates for given sample position.
     *
     * @param x the x-index of the sample
     * @param y the y-index of the sample
     * @param z the z-index of the sample
     *
     * @return the world coordinates for the sample
     */
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

    /**
     * Marks the sample as removed from the queue.
     *
     * @param x the x-coordinate of the sample
     * @param y the y-coordinate of the sample
     * @param z the z-coordinate of the sample
     */
    public void popQueued(final int x, final int y, final int z) {
        getChunkAt(x, y, z).popQueued(x, y, z);
    }

    /**
     * Queries whether or not the chunk where the given sample coordinate belongs to is ready. If
     * this returns true, mesh should be generated.
     * <p>
     * Note: This is only heuristic and the "readiness" may change later on.
     *
     * @param x the x-coordinate of the sample
     * @param y the y-coordinate of the sample
     * @param z the z-coordinate of the sample
     *
     * @return <code>true</code> if the chunk is ready
     */
    public boolean isChunkReady(final int x, final int y, final int z) {
        return getChunkAt(x, y, z).isReady();
    }

    /**
     * Destroys all empty (fully non-solid) chunks.
     */
    public void compact() {
        final var keys = this.chunks.keys();
        var count = 0;
        for (final var index : keys) {
            final var chunk = this.chunks.get(index);
            if (chunk != null && !chunk.hasSolidSamples()) {
                this.chunks.put(index, null);
                ++count;
            }
        }
        PROFILER.log("-> Compacted away {} chunks", count);
    }
}
