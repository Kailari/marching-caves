package caves.generator.density;

import caves.util.math.Vector3;

/**
 * Density function for a single node.
 */
@SuppressWarnings("SameParameterValue")
public final class EdgeDensityFunction {
    private final double caveMainInfluenceRadius;
    private final double maxInfluenceRadius;
    private final double pathFloorInfluenceRadius;
    private final double floorFlatness;
    private final Vector3 directionUp;

    private final SimplexNoiseGenerator noiseGenerator;
    private final float noiseScale;
    private final double floorStart;
    private final double globalNoiseFactor;
    private final double globalNoiseMagnitude;

    /**
     * Creates a new density function for calculating densities for a single edge.
     *
     * @param maxInfluenceRadius the maximum distance from the path after which the function is
     *                           guaranteed to return zero values
     * @param caveRadius         radius of the main cave path. This is the radius of influence
     *                           around the path edges
     * @param floorFlatness      "flatness" of cave floor, lower values make the floor more round
     */
    public EdgeDensityFunction(
            final double maxInfluenceRadius,
            final double caveRadius,
            final double floorFlatness
    ) {
        this.caveMainInfluenceRadius = caveRadius;
        this.pathFloorInfluenceRadius = caveRadius / 2.5;
        this.maxInfluenceRadius = maxInfluenceRadius;

        this.floorFlatness = floorFlatness;
        this.directionUp = new Vector3(0.0f, 1.0f, 0.0f);

        this.noiseGenerator = new SimplexNoiseGenerator(42);
        this.floorStart = 0.2;
        this.noiseScale = 0.0125f;
        this.globalNoiseMagnitude = 1.0;
        this.globalNoiseFactor = 0.75;
    }

    /**
     * Linear interpolation.
     *
     * @param a first value
     * @param b second value
     * @param t alpha
     *
     * @return linearly interpolated value between a and b
     */
    public static double lerp(final double a, final double b, final double t) {
        return (1 - t) * a + t * b;
    }

    /**
     * Calculates the density contribution for the given point from the cave path edge defined by
     * nodes A and B.
     *
     * @param position     position for which to calculate the density
     * @param closestPoint the point on the edge that is closest to the given position
     * @param distanceSq   squared distance between the closest point and the position
     *
     * @return the density contribution
     */
    public float apply(
            final Vector3 position,
            final Vector3 closestPoint,
            final float distanceSq
    ) {
        // Optimization: Skip everything further than max influence radius away
        final var influenceRadiusSq = this.maxInfluenceRadius * this.maxInfluenceRadius;
        if (distanceSq > influenceRadiusSq) {
            return 0.0f;
        }

        // Special case: The point being sampled is *exactly* on the node. This guarantees that
        //               direction vectors are non-zero later on.
        if (distanceSq < 0.0000001) {
            return -1.0f;
        }

        final var distance = Math.sqrt(distanceSq);
        final var distanceAlpha = Math.min(1.0, distance / this.caveMainInfluenceRadius);
        final var caveDensity = lerp(1.0, 0.0, distanceAlpha);

        final var verticalDistance = closestPoint.getY() - position.getY();
        final double caveContribution;
        double floorWeight;
        if (verticalDistance > 0) {
            // Calculate floor bias if we are below the path
            final var direction = closestPoint.sub(position, new Vector3()).normalize();

            floorWeight = Math.max(0.0, direction.dot(this.directionUp) - this.floorStart);
            assert floorWeight >= 0.0 && floorWeight <= 1.0;
            floorWeight *= floorWeight;

            final var distanceToFloorAlpha = Math.min(1.0, verticalDistance / this.pathFloorInfluenceRadius);

            final var floorDensity = lerp(1.0, 0.0, distanceToFloorAlpha);

            // Return as negative to "decrease the density" around the edge.
            caveContribution = -lerp(caveDensity, floorDensity, floorWeight * this.floorFlatness);
        } else {
            // Above path, guaranteed to not be floor
            caveContribution = -caveDensity;
            floorWeight = 0.0;
        }

        final var globalNoiseMultiplier = distanceAlpha * (1.0 - floorWeight);
        final var globalNoise = -getGlobalNoise(position) * globalNoiseMultiplier;
        final var globalDensity = lerp(caveContribution, globalNoise,
                                       this.globalNoiseFactor * distanceAlpha);

        // Ensures that walls are solid after max radius
        final var fadeToSolidAlpha = Math.min(1.0, distance / this.maxInfluenceRadius);
        final var clampedDensity = lerp(globalDensity, 0.0, fadeToSolidAlpha);
        return (float) clampedDensity;
    }

    private double getGlobalNoise(final Vector3 pos) {
        return this.noiseGenerator.evaluate(pos.mul(this.noiseScale, new Vector3())) * this.globalNoiseMagnitude;
    }
}
