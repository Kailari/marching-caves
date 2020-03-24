package caves.generator;

import caves.generator.util.Vector3;

import java.util.Arrays;
import java.util.function.BiFunction;

public final class CaveSampleSpace {
    private final int countX;
    private final int countY;
    private final int countZ;
    private final float[] samples;
    private final float samplesPerUnit;
    private final Vector3 min;
    private final Vector3 max;
    private final BiFunction<CavePath, Vector3, Float> densityFunction;
    private final CavePath cavePath;

    public int getCountX() {
        return this.countX;
    }

    public int getCountY() {
        return this.countY;
    }

    public int getCountZ() {
        return this.countZ;
    }

    public int getTotalCount() {
        return this.countX * this.countY * this.countZ;
    }

    public Vector3 getMin() {
        return this.min;
    }

    public Vector3 getMax() {
        return this.max;
    }

    public CaveSampleSpace(
            final CavePath cavePath,
            final float margin,
            final float samplesPerUnit,
            final BiFunction<CavePath, Vector3, Float> densityFunction
    ) {
        System.out.println("Initializing a sample space");

        this.samplesPerUnit = samplesPerUnit;
        this.densityFunction = densityFunction;
        this.cavePath = cavePath;

        final var nodes = cavePath.getNodesOrdered();

        this.min = Arrays.stream(nodes)
                         .reduce(new Vector3(Float.POSITIVE_INFINITY,
                                             Float.POSITIVE_INFINITY,
                                             Float.POSITIVE_INFINITY),
                                 (acc, b) -> acc.min(b, acc))
                         .sub(margin, margin, margin, new Vector3());
        this.max = Arrays.stream(nodes)
                         .reduce(new Vector3(Float.NEGATIVE_INFINITY,
                                             Float.NEGATIVE_INFINITY,
                                             Float.NEGATIVE_INFINITY),
                                 (acc, b) -> acc.max(b, acc))
                         .add(margin, margin, margin, new Vector3());

        final var sizeX = Math.abs(this.max.getX() - this.min.getX());
        final var sizeY = Math.abs(this.max.getY() - this.min.getY());
        final var sizeZ = Math.abs(this.max.getZ() - this.min.getZ());

        this.countX = (int) (sizeX * samplesPerUnit);
        this.countY = (int) (sizeY * samplesPerUnit);
        this.countZ = (int) (sizeZ * samplesPerUnit);
        this.samples = new float[this.countX * this.countY * this.countZ];

        System.out.printf("\t-> boundaries (%.3f, %.3f, %.3f)\n",
                          sizeX, sizeY, sizeZ);
        System.out.printf("\t-> maximum count of %d samples (%d x %d x %d).\n",
                          this.countX * this.countY * this.countZ,
                          this.countX, this.countY, this.countZ);
    }

    public int getSampleIndex(final int x, final int y, final int z) {
        return x + (z * this.countX) + (y * this.countX * this.countZ);
    }

    public float getDensity(final int sampleIndex, final int x, final int y, final int z) {
        if (this.samples[sampleIndex] == 0.0f) {
            calculateDensity(sampleIndex, x, y, z);
        }

        return this.samples[sampleIndex];
    }

    public float getDensity(final int x, final int y, final int z) {
        final var sampleIndex = getSampleIndex(x, y, z);
        if (this.samples[sampleIndex] == 0.0f) {
            calculateDensity(sampleIndex, x, y, z);
        }

        return this.samples[sampleIndex];
    }

    public Vector3 getPos(final int x, final int y, final int z) {
        return new Vector3(this.min.getX() + x * (1.0f / this.samplesPerUnit),
                           this.min.getY() + y * (1.0f / this.samplesPerUnit),
                           this.min.getZ() + z * (1.0f / this.samplesPerUnit));
    }

    private void calculateDensity(final int sampleIndex, final int x, final int y, final int z) {
        final var pos = getPos(x, y, z);
        final var density = this.densityFunction.apply(this.cavePath, pos);
        this.samples[sampleIndex] = density;
    }
}
