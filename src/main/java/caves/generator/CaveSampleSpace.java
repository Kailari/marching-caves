package caves.generator;

import caves.util.math.BoundingBox;
import caves.util.math.Vector3;

import java.util.function.Function;

import static caves.util.profiler.Profiler.PROFILER;

public final class CaveSampleSpace {
    private final int countX;
    private final int countY;
    private final int countZ;
    private final float[] samples;
    private final float samplesPerUnit;
    private final BoundingBox bounds;
    private final Function<Vector3, Float> densityFunction;

    /**
     * Gets the number of samples on the x-axis.
     *
     * @return the sample count
     */
    public int getCountX() {
        return this.countX;
    }

    /**
     * Gets the number of samples on the y-axis.
     *
     * @return the sample count
     */
    public int getCountY() {
        return this.countY;
    }

    /**
     * Gets the number of samples on the z-axis.
     *
     * @return the sample count
     */
    public int getCountZ() {
        return this.countZ;
    }

    /**
     * Gets the maximum total number of samples that can potentially fit into this sample space.
     * Equal to per-axis counts multiplied by each other.
     *
     * @return the maximum potential sample count
     */
    public int getTotalCount() {
        return this.countX * this.countY * this.countZ;
    }

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
     * Gets the sample resolution per unit for this sampling space.
     *
     * @return samples per unit
     */
    public float getSamplesPerUnit() {
        return this.samplesPerUnit;
    }

    /**
     * Initializes a new sample space. This alone performs some allocations, but the actual
     * densities for the samples are calculated lazily.
     *
     * @param cavePath        path for influencing the sample density
     * @param margin          margin size around the path. This generally should be greater than or
     *                        equal to the maximum influence radius of a single path node
     * @param samplesPerUnit  how many samples to fit per one unit of space
     * @param densityFunction the density function for calculating sample density
     */
    public CaveSampleSpace(
            final CavePath cavePath,
            final float margin,
            final float samplesPerUnit,
            final Function<Vector3, Float> densityFunction
    ) {
        this.samplesPerUnit = samplesPerUnit;
        this.densityFunction = densityFunction;
        this.bounds = new BoundingBox(cavePath.getAllNodes(), margin);

        final var sizeX = Math.abs(getMax().getX() - getMin().getX());
        final var sizeY = Math.abs(getMax().getY() - getMin().getY());
        final var sizeZ = Math.abs(getMax().getZ() - getMin().getZ());

        this.countX = (int) (sizeX * samplesPerUnit);
        this.countY = (int) (sizeY * samplesPerUnit);
        this.countZ = (int) (sizeZ * samplesPerUnit);
        this.samples = new float[this.countX * this.countY * this.countZ];

        PROFILER.log("-> boundaries {}", String.format("(%.3f, %.3f, %.3f)", sizeX, sizeY, sizeZ));
        PROFILER.log("-> maximum count of {} samples {}.",
                     getTotalCount(),
                     String.format("(%d x %d x %d)", this.countX, this.countY, this.countZ));
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
        return x + (z * this.countX) + (y * this.countX * this.countZ);
    }

    /**
     * Calculates the density for the sample at given per-axis sample indices. Overload for {@link
     * #getDensity(int, int, int)} for situations where sample index is already known. The given
     * sample index must equal to one returned by {@link #getSampleIndex(int, int, int)} for the
     * per-axis indices <code>(x, y, z)</code>.
     *
     * @param sampleIndex the sample index
     * @param x           index of the sample on the x-axis
     * @param y           index of the sample on the y-axis
     * @param z           index of the sample on the z-axis
     *
     * @return the density of the sample
     */
    public float getDensity(final int sampleIndex, final int x, final int y, final int z) {
        if (this.samples[sampleIndex] == 0.0f) {
            calculateDensity(sampleIndex, x, y, z);
        }

        return this.samples[sampleIndex];
    }

    /**
     * Calculates density for the sample at given per-axis sample indices. Overload for {@link
     * #getDensity(int, int, int, int)} for situations where sample index is not yet known.
     * Internally calculates the sample index.
     *
     * @param x index of the sample on the x-axis
     * @param y index of the sample on the y-axis
     * @param z index of the sample on the z-axis
     *
     * @return the density of the sample
     */
    public float getDensity(final int x, final int y, final int z) {
        final var sampleIndex = getSampleIndex(x, y, z);
        if (this.samples[sampleIndex] == 0.0f) {
            calculateDensity(sampleIndex, x, y, z);
        }

        return this.samples[sampleIndex];
    }

    /**
     * Gets the in-world coordinates for a given per-axis sample indices.
     *
     * @param x index of the sample on the x-axis
     * @param y index of the sample on the y-axis
     * @param z index of the sample on the z-axis
     *
     * @return the in-world coordinates
     */
    public Vector3 getPos(final int x, final int y, final int z) {
        final var spaceBetweenSamples = (1.0f / this.samplesPerUnit);
        return new Vector3(this.bounds.getMin().getX() + x * spaceBetweenSamples,
                           this.bounds.getMin().getY() + y * spaceBetweenSamples,
                           this.bounds.getMin().getZ() + z * spaceBetweenSamples);
    }

    private void calculateDensity(final int sampleIndex, final int x, final int y, final int z) {
        this.samples[sampleIndex] = this.densityFunction.apply(getPos(x, y, z));
    }
}
