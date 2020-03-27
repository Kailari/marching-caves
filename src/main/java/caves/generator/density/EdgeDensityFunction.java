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
        if (true) {
            return this.noiseGenerator.evaluate(pos.mul(this.noiseScale, new Vector3()));
        }

        final var closestPoint = LineSegment.closestPoint(nodeA, nodeB, pos);

        final var distanceSq = closestPoint.distanceSq(pos);
        if (distanceSq > this.pathInfluenceRadius * this.pathInfluenceRadius) {
            return 0.0f;
        }

        final var distance = Math.sqrt(distanceSq);
        final var clampedDistanceAlpha = Math.min(1.0, distance / this.pathInfluenceRadius);
        final var caveDensity = baseDensityCurve(clampedDistanceAlpha);

        final var direction = closestPoint.sub(pos, new Vector3()).normalize();

        // Dot product can kind of be thought as to signify "how perpendicular two vectors are?"
        // or "what is the size of the portion of these two vectors that overlaps?". Here, we
        // are working with up axis and a direction, thus taking their dot product in this
        // context practically means "how upwards the direction vector points".
        //
        // Both are unit vectors so resulting scalar has maximum absolute value of 1.0.
        //
        // Furthermore, for the ceiling the dot product is negative, so by clamping to zero we
        // get a nice weight multiplier for the floor. (The resulting value is zero for walls
        // and the ceiling).
        //
        // From there, just lerp between the base density and higher density (based on the base
        // density) to get a nice flat floor.
        final var floorWeight = Math.max(0.0, direction.dot(this.directionUp) * this.floorFlatness);
        assert floorWeight >= 0.0 && floorWeight <= 1.0;

        final var verticalDistance = Math.abs(pos.getY() - closestPoint.getY());
        final var distanceToFloorAlpha = Math.min(1.0, verticalDistance / this.pathFloorInfluenceRadius);

        final var floorDensity = floorDensityCurve(distanceToFloorAlpha);

        // Return as negative to "decrease the density" around the edge.
        final var caveContribution = -lerp(caveDensity, floorDensity, floorWeight);
        final var globalNoiseContribution = -getGlobalNoise(pos) * (1.0 - clampedDistanceAlpha);

        final double globalNoiseCoefficient = (1.0 - floorWeight) * 0.5;
        return (float) lerp(caveContribution, globalNoiseContribution, globalNoiseCoefficient);
    }

    private double getGlobalNoise(final Vector3 pos) {
        return this.noiseGenerator.evaluate(pos.mul(this.noiseScale, new Vector3()));
    }
}
