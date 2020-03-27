package caves.generator.density;

import caves.util.math.LineSegment;
import caves.util.math.Vector3;

/**
 * Density function for a single node.
 */
@SuppressWarnings("SameParameterValue")
public final class EdgeDensityFunction {
    private final double pathInfluenceRadius;
    private final double pathFloorInfluenceRadius;
    private final double floorFlatness;
    private final Vector3 directionUp;

    private final SimplexNoiseGenerator noiseGenerator;
    private final float noiseScale;

    /**
     * Creates a new density function for calculating densities for a single edge.
     *
     * @param pathInfluenceRadius how far the path influences the sample densities
     * @param floorFlatness       "flatness" of cave floor, lower values make the floor more round
     */
    public EdgeDensityFunction(
            final double pathInfluenceRadius,
            final double floorFlatness
    ) {
        this.pathInfluenceRadius = pathInfluenceRadius;
        this.pathFloorInfluenceRadius = pathInfluenceRadius / 2.5;
        this.floorFlatness = floorFlatness;
        this.directionUp = new Vector3(0.0f, 1.0f, 0.0f);

        this.noiseGenerator = new SimplexNoiseGenerator(42);
        this.noiseScale = 0.025f;
    }

    private static double baseDensityCurve(final double t) {
        return Math.min(1.0, lerp(1.0, 0.0, t));
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

    private double floorDensityCurve(final double t) {
        return baseDensityCurve(t);
    }

    /**
     * Calculates the density contribution for the given point from the cave path edge defined by
     * nodes A and B.
     *
     * @param nodeA the start point of the edge
     * @param nodeB the end point of the edge
     * @param pos   position for which to calculate the density
     *
     * @return the density contribution
     */
    public float apply(final Vector3 nodeA, final Vector3 nodeB, final Vector3 pos) {
        final var closestPoint = LineSegment.closestPoint(nodeA, nodeB, pos);

        final var distanceSq = closestPoint.distanceSq(pos);
        // HACK:    Add extra margin by incrementing the exponent to 3. This allows overflow from
        //          other sources of density. (e.g. overlaid noise functions)
        if (distanceSq > this.pathInfluenceRadius * this.pathInfluenceRadius * this.pathInfluenceRadius) {
            //return 0.0f;
        }

        final var distance = Math.sqrt(distanceSq);
        final var clampedDistanceAlpha = Math.min(1.0, distance / this.pathInfluenceRadius);
        final var caveDensity = baseDensityCurve(clampedDistanceAlpha);

        final var direction = closestPoint.sub(pos, new Vector3()).normalize();

        final var floorWeight = Math.max(0.0, direction.dot(this.directionUp) * this.floorFlatness);
        assert floorWeight >= 0.0 && floorWeight <= 1.0;

        final var verticalDistance = Math.abs(pos.getY() - closestPoint.getY());
        final var distanceToFloorAlpha = Math.min(1.0, verticalDistance / this.pathFloorInfluenceRadius);

        final var floorDensity = floorDensityCurve(distanceToFloorAlpha);

        // Return as negative to "decrease the density" around the edge.
        final var caveContribution = -lerp(caveDensity, floorDensity, floorWeight);
        final var globalNoiseContribution = -getGlobalNoise(pos) * clampedDistanceAlpha;

        final var floorNoisiness = 0.35;
        final var overallNoisiness = 0.35;
        final double globalNoiseCoefficient = (1.0 - floorWeight * (1.0 - floorNoisiness)) * overallNoisiness;
        return (float) lerp(caveContribution, globalNoiseContribution, globalNoiseCoefficient);
    }

    private double getGlobalNoise(final Vector3 pos) {
        return this.noiseGenerator.evaluate(pos.mul(this.noiseScale, new Vector3()));
    }
}
