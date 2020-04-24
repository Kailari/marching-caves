package caves.generator;

import caves.generator.density.DensityFunction;
import caves.util.collections.ChunkMap;
import caves.util.math.Vector3;

import static caves.util.math.MathUtil.fastFloor;

public final class ChunkCaveSampleSpace {
    /**
     * Size of a sample chunk. Same size is used on all three axes.
     */
    public static final int CHUNK_SIZE = 32;

    private final ChunkMap chunks;
    private final DensityFunction densityFunction;
    private final float samplesPerUnit;

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
     * Gets the total vertex count in this sample space.
     *
     * @return the total number of vertices
     */
    public int getTotalVertices() {
        var count = 0;
        for (final var chunk : this.chunks.values()) {
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
            final DensityFunction densityFunction,
            final float surfaceLevel
    ) {
        this.densityFunction = densityFunction;
        this.samplesPerUnit = samplesPerUnit;

        this.chunks = new ChunkMap(32768, surfaceLevel);
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
     * @param x      sample x index
     * @param y      sample y index
     * @param z      sample z index
     * @param tmpPos vector to use as the temporary position vector
     *
     * @return the density of the specified sample
     */
    public float getDensity(final int x, final int y, final int z, final Vector3 tmpPos) {
        return getChunkAt(x, y, z).getDensity(x, y, z,
                                              () -> this.densityFunction.apply(getPos(x, y, z, tmpPos)));
    }

    /**
     * Gets in-world coordinates for given sample position.
     *
     * @param x      the x-index of the sample
     * @param y      the y-index of the sample
     * @param z      the z-index of the sample
     * @param result vector to hold the result
     *
     * @return the world coordinates for the sample
     */
    public Vector3 getPos(final int x, final int y, final int z, final Vector3 result) {
        final var spaceBetweenSamples = (1.0f / this.samplesPerUnit);
        return result.set(x * spaceBetweenSamples,
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
        return this.chunks.createIfAbsent(chunkIndex);
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
}
