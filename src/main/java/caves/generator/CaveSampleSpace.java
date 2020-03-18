package caves.generator;

import caves.generator.util.Vector3;

import java.util.Arrays;
import java.util.function.BiFunction;

public final class CaveSampleSpace {
    private final float sampleSpaceSizeX;
    private final float sampleSpaceSizeY;
    private final float sampleSpaceSizeZ;
    private final int sampleCountX;
    private final int sampleCountY;
    private final int sampleCountZ;
    private final float[] samples;
    private final float margin;
    private final float resolution;
    private final Vector3 min;
    private final Vector3 max;
    private Vector3[] positions;

    public float getSizeX() {
        return this.sampleSpaceSizeX;
    }

    public float getSizeY() {
        return this.sampleSpaceSizeY;
    }

    public float getSizeZ() {
        return this.sampleSpaceSizeZ;
    }

    public int getCountX() {
        return this.sampleCountX;
    }

    public int getCountY() {
        return this.sampleCountY;
    }

    public int getCountZ() {
        return this.sampleCountZ;
    }

    public Vector3 getMin() {
        return this.min;
    }

    public Vector3 getMax() {
        return this.max;
    }

    public int getSize() {
        return this.sampleCountX * this.sampleCountY * this.sampleCountZ;
    }

    public CaveSampleSpace(
            final CavePath cave,
            final float margin,
            final float resolution,
            final BiFunction<CavePath, Vector3, Float> densityFunction
    ) {
        final var nodes = cave.getNodesOrdered();

        this.min = Arrays.stream(nodes)
                         .reduce(new Vector3(Float.POSITIVE_INFINITY,
                                             Float.POSITIVE_INFINITY,
                                             Float.POSITIVE_INFINITY),
                                 (acc, b) -> acc.min(b, acc));
        this.max = Arrays.stream(nodes)
                         .reduce(new Vector3(Float.NEGATIVE_INFINITY,
                                             Float.NEGATIVE_INFINITY,
                                             Float.NEGATIVE_INFINITY),
                                 (acc, b) -> acc.max(b, acc));

        this.margin = margin;
        this.sampleSpaceSizeX = Math.abs(this.max.getX() - this.min.getX()) + 2 * this.margin;
        this.sampleSpaceSizeY = Math.abs(this.max.getY() - this.min.getY()) + 2 * this.margin;
        this.sampleSpaceSizeZ = Math.abs(this.max.getZ() - this.min.getZ()) + 2 * this.margin;

        this.resolution = resolution;
        this.sampleCountX = (int) (this.sampleSpaceSizeX / this.resolution);
        this.sampleCountY = (int) (this.sampleSpaceSizeY / this.resolution);
        this.sampleCountZ = (int) (this.sampleSpaceSizeZ / this.resolution);
        this.samples = new float[this.sampleCountX * this.sampleCountY * this.sampleCountZ];
        this.positions = new Vector3[this.sampleCountX * this.sampleCountY * this.sampleCountZ];

        for (int x = 0; x < this.sampleCountX; ++x) {
            for (int y = 0; y < this.sampleCountY; ++y) {
                for (int z = 0; z < this.sampleCountZ; ++z) {
                    final var sampleIndex = x + (z * this.sampleCountX) + (y * this.sampleCountX * this.sampleCountZ);
                    final var pos = new Vector3((this.min.getX() + x * this.resolution) - this.margin,
                                                (this.min.getY() + y * this.resolution) - this.margin,
                                                (this.min.getZ() + z * this.resolution) - this.margin);

                    final var density = densityFunction.apply(cave, pos);
                    this.samples[sampleIndex] = density;
                    this.positions[sampleIndex] = pos;
                }
            }
        }
    }

    public float getDensity(final int sampleIndex) {
        return this.samples[sampleIndex];
    }

    public Vector3 getPos(final int sampleIndex) {
        return this.positions[sampleIndex];
    }
}
