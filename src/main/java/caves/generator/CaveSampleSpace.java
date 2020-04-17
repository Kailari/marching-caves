package caves.generator;

import caves.util.math.BoundingBox;
import caves.util.math.Vector3;

import java.util.Arrays;
import java.util.function.Supplier;

import static caves.generator.ChunkCaveSampleSpace.CHUNK_SIZE;

public final class CaveSampleSpace {
    private final float[] samples;
    private final BoundingBox bounds;
    private final boolean[] queued;

    /**
     * Gets the component-wise minimum possible world-coordinates for this sample space. This is the
     * point <code>(0, 0, 0)</code> for samples.
     *
     * @return the starting in-world coordinate for this sample space
     */
    public Vector3 getMin() {
        return this.bounds.getMin();
    }

    /**
     * Gets the component-wise minimum possible world-coordinates for this sample space. This is the
     * point <code>(countX, countY, countZ)</code> for samples.
     *
     * @return the maximum in-world coordinate for this sample space
     */
    public Vector3 getMax() {
        return this.bounds.getMax();
    }

    /**
     * Creates a new sample space for the given region of space.
     *
     * @param startX start x-coordinate of the region
     * @param startY start y-coordinate of the region
     * @param startZ start z-coordinate of the region
     * @param size   size on the x-axis (in units)
     */
    public CaveSampleSpace(
            final float startX,
            final float startY,
            final float startZ,
            final float size
    ) {
        this.bounds = new BoundingBox(new Vector3(startX, startY, startZ),
                                      new Vector3(startX + size, startY + size, startZ + size));

        final var sampleCount = CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE;
        this.samples = new float[sampleCount];
        this.queued = new boolean[sampleCount];
        Arrays.fill(this.samples, Float.NaN);
    }

    /**
     * Calculates sample index for the given per-axis sample indices.
     *
     * @param x index of the sample on the x-axis
     * @param y index of the sample on the y-axis
     * @param z index of the sample on the z-axis
     *
     * @return the sample index
     */
    public int getSampleIndex(final int x, final int y, final int z) {
        final var localX = Math.abs(x % CHUNK_SIZE);
        final var localY = Math.abs(y % CHUNK_SIZE);
        final var localZ = Math.abs(z % CHUNK_SIZE);
        return localX + localZ * CHUNK_SIZE + localY * CHUNK_SIZE * CHUNK_SIZE;
    }

    /**
     * Calculates density for the sample at given per-axis sample indices.
     *
     * @param x index of the sample on the x-axis
     * @param y index of the sample on the y-axis
     * @param z index of the sample on the z-axis
     *
     * @return the density of the sample
     */
    public float getDensity(
            final int x,
            final int y,
            final int z,
            final Supplier<Float> densityFunction
    ) {
        final var sampleIndex = getSampleIndex(x, y, z);
        if (Float.isNaN(this.samples[sampleIndex])) {
            this.samples[sampleIndex] = densityFunction.get();
        }

        return this.samples[sampleIndex];
    }

    public boolean markQueued(final int x, final int y, final int z) {
        final var sampleIndex = getSampleIndex(x, y, z);

        if (this.queued[sampleIndex]) {
            return false;
        }

        this.queued[sampleIndex] = true;
        return true;
    }
}
