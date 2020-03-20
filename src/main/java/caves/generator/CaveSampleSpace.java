package caves.generator;

import caves.generator.util.Vector3;

import java.util.Arrays;
import java.util.function.BiFunction;

public final class CaveSampleSpace {
    private final float sizeX;
    private final float sizeY;
    private final float sizeZ;
    private final int countX;
    private final int countY;
    private final int countZ;
    private final float[] samples;
    private final float margin;
    private final float resolution;
    private final Vector3 min;
    private final Vector3 max;
    private final BiFunction<CavePath, Vector3, Float> densityFunction;
    private final CavePath cavePath;

    public float getSizeX() {
        return this.sizeX;
    }

    public float getSizeY() {
        return this.sizeY;
    }

    public float getSizeZ() {
        return this.sizeZ;
    }

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
            final float resolution,
            final BiFunction<CavePath, Vector3, Float> densityFunction
    ) {
        System.out.println("Initializing a sample space");

        this.resolution = resolution;
        this.margin = margin;
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

        this.sizeX = Math.abs(this.max.getX() - this.min.getX());
        this.sizeY = Math.abs(this.max.getY() - this.min.getY());
        this.sizeZ = Math.abs(this.max.getZ() - this.min.getZ());

        this.countX = (int) (this.sizeX / resolution);
        this.countY = (int) (this.sizeY / resolution);
        this.countZ = (int) (this.sizeZ / resolution);
        this.samples = new float[this.countX * this.countY * this.countZ];

        System.out.printf("\t-> boundaries (%.3f, %.3f, %.3f)\n",
                          this.sizeX, this.sizeY, this.sizeZ);
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
        return new Vector3(this.min.getX() + x * this.resolution,
                           this.min.getY() + y * this.resolution,
                           this.min.getZ() + z * this.resolution);
    }

    private void calculateDensity(final int sampleIndex, final int x, final int y, final int z) {
        final var pos = new Vector3(this.min.getX() + x * this.resolution,
                                    this.min.getY() + y * this.resolution,
                                    this.min.getZ() + z * this.resolution);

        final var density = this.densityFunction.apply(this.cavePath, pos);
        this.samples[sampleIndex] = density;
    }
}
