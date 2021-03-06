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
    private final double floorStart;

    private final Vector3 directionUp;

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

        this.floorStart = 0.2;
        this.floorFlatness = floorFlatness;
        this.directionUp = new Vector3(0.0f, 1.0f, 0.0f);
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
     * @param result       object to hold the results
     *
     * @return the density contribution
     */
    public NodeContribution apply(
            final Vector3 position,
            final Vector3 closestPoint,
            final float distanceSq,
            final NodeContribution result
    ) {
        // Optimization: Skip everything further than max influence radius away
        final var influenceRadiusSq = this.maxInfluenceRadius * this.maxInfluenceRadius;
        if (distanceSq > influenceRadiusSq) {
            result.clear(false);
            return result;
        }

        // Special case: The point being sampled is *exactly* on the node. This guarantees that
        //               direction vectors are non-zero later on.
        if (distanceSq < 0.0000001) {
            result.clear(true);
            return result;
        }

        final var distance = Math.sqrt(distanceSq);
        final var distanceAlpha = Math.min(1.0, distance / this.caveMainInfluenceRadius);
        final var caveDensity = lerp(1.0, 0.0, distanceAlpha);

        final var verticalDistance = closestPoint.getY() - position.getY();
        final double caveContribution;
        double floorWeight;
        if (verticalDistance > 0) {
            // Calculate floor bias if we are below the path.
            // XXX: Do not allocate new vector. The closest point is tmpResult from PathDensityFunction
            //      and is not used after this point
            final var direction = closestPoint.sub(position/*, new Vector3()*/).normalize();

            // This is actually
            //   max(0, (direction dot directionUp) - this.floorStart)
            // but as direction x/y are always zero, we can optimize them away
            floorWeight = Math.max(0.0, direction.y * this.directionUp.y - this.floorStart);
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

        result.set(floorWeight, caveContribution, distance, distanceAlpha, true);
        return result;
    }
}
