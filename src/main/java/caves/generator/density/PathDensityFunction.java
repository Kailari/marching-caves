package caves.generator.density;

import caves.generator.CavePath;
import caves.util.math.LineSegment;
import caves.util.math.Vector3;

import java.util.function.Function;

import static caves.generator.density.EdgeDensityFunction.lerp;

public final class PathDensityFunction implements Function<Vector3, Float> {
    private static final double WEIGHT_EPSILON = 0.0001;
    private static final double WEIGHT_EPSILON_SQ = WEIGHT_EPSILON * WEIGHT_EPSILON;

    private final CavePath cavePath;
    private final double maxInfluenceRadius;
    private final double caveMainInfluenceRadius;
    private final EdgeDensityFunction edgeDensityFunction;

    private final Vector3 tmpResult = new Vector3();

    private final SimplexNoiseGenerator noiseGenerator;
    private final float noiseScale;
    private final double globalNoiseFactor;
    private final double globalNoiseMagnitude;

    /**
     * Creates a new density function for a cave path. The function calculates point densities as
     * sum of nearby path edges' densities.
     *
     * @param cavePath            contributing path
     * @param caveRadius          radius of the main cave
     * @param maxInfluenceRadius  maximum node influence radius after noise is applied
     * @param edgeDensityFunction the density function to use for calculating edge density
     */
    public PathDensityFunction(
            final CavePath cavePath,
            final double caveRadius,
            final double maxInfluenceRadius,
            final EdgeDensityFunction edgeDensityFunction
    ) {
        this.cavePath = cavePath;
        this.caveMainInfluenceRadius = caveRadius;
        this.maxInfluenceRadius = maxInfluenceRadius;
        this.edgeDensityFunction = edgeDensityFunction;

        this.noiseGenerator = new SimplexNoiseGenerator(42);
        this.noiseScale = 0.01f;
        this.globalNoiseMagnitude = 1.0;
        this.globalNoiseFactor = 1.00;
    }

    @Override
    public Float apply(final Vector3 position) {
        final var nodes = this.cavePath.getNodesWithin(position, this.maxInfluenceRadius);

        var summedWeights = 0.0;
        var nContributions = 0;
        var weightedTotal = 0.0;
        var minDistance = Double.MAX_VALUE;
        var summedFloorness = 0.0;
        final var edgeResult = new NodeContribution();
        for (final var nodeIndex : nodes) {
            edgeResult.setValue(0.0);
            edgeResult.setWeight(0.0);
            edgeResult.setFloorness(0.0);
            edgeResult.setHasContribution(false);

            final int previous = this.cavePath.getPreviousFor(nodeIndex);
            if (previous != -1) {
                calculateEdge(edgeResult, previous, nodeIndex, position);
            }
            for (final int n : this.cavePath.getNextFor(nodeIndex)) {
                calculateEdge(edgeResult, nodeIndex, n, position);
            }

            if (edgeResult.hasContribution()) {
                final var weight = edgeResult.getWeight();
                weightedTotal += weight * edgeResult.getValue();
                summedFloorness += weight * edgeResult.getFloorness();
                minDistance = Math.min(minDistance, edgeResult.getDistance());

                summedWeights += weight;
                nContributions++;
            }
        }

        if (nContributions == 0) {
            return 1.0f;
        }

        final var weightedAverage = weightedTotal / summedWeights;
        final var floorness = summedFloorness / summedWeights;

        final var distanceAlpha = Math.min(1.0, minDistance / this.caveMainInfluenceRadius);
        final var globalNoiseMultiplier = distanceAlpha * (1.0 - floorness);
        final var globalNoise = -getGlobalNoise(position) * globalNoiseMultiplier;
        final var globalDensity = lerp(weightedAverage, globalNoise, this.globalNoiseFactor * distanceAlpha);

        // Ensures that walls are solid after max radius
        final var fadeToSolidAlpha = Math.min(1.0, minDistance / this.maxInfluenceRadius);
        final var clampedDensity = lerp(globalDensity, 0.0, fadeToSolidAlpha);
        return (float) Math.max(0.0, Math.min(1.0, 1.0 + clampedDensity));
    }

    private double getGlobalNoise(final Vector3 pos) {
        return this.noiseGenerator.evaluate(pos.mul(this.noiseScale, new Vector3())) * this.globalNoiseMagnitude;
    }

    private void calculateEdge(
            final NodeContribution result,
            final int indexA,
            final int indexB,
            final Vector3 position
    ) {
        final var maxRadiusSq = this.maxInfluenceRadius * this.maxInfluenceRadius;

        final var nodeA = PathDensityFunction.this.cavePath.get(indexA);
        final var nodeB = PathDensityFunction.this.cavePath.get(indexB);
        final var closest = LineSegment.closestPoint(nodeA, nodeB, position, this.tmpResult);

        final var distanceSq = closest.distanceSq(position);
        if (distanceSq > maxRadiusSq) {
            return;
        }

        // HACK:    Avoid potentially getting very large whole number part for the weight by
        //          rescaling the weights to 0..maxRadius. However, we do not want to calculate
        //          square root here, so use squares of rescaled weights instead:
        //
        //              w   = sqrt(distance^2) / maxRadius
        //              w^2 =      distance^2  / maxRadius^2
        //
        //          This works because the weighted average does not care which scale the
        //          weights use, as long as all weights are on the same scale. As distances are
        //          always less than max radius, we get nice weight range of 0..1, where
        //          individual weights are on exponential (power two) curve. This gives us more
        //          than enough accuracy to avoid visual artifacts in most cases.
        final var weightSq = 1.0 - Math.min(1.0, distanceSq / maxRadiusSq);

        if (weightSq < WEIGHT_EPSILON_SQ) {
            return;
        }

        final var contribution = this.edgeDensityFunction.apply(position, closest, distanceSq);
        if (contribution.hasContribution()) {
            result.setValue(Math.min(result.getValue(), contribution.getValue()));
            result.setWeight(Math.max(result.getWeight(), weightSq));
            result.setDistance(Math.min(result.getDistance(), contribution.getDistance()));
            result.setFloorness(Math.min(result.getFloorness(), contribution.getFloorness()));
            result.setHasContribution(true);
        }
    }
}
