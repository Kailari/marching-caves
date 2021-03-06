package caves.generator.density;

import caves.generator.CavePath;
import caves.util.math.LineSegment;
import caves.util.math.Vector3;

import static caves.generator.density.EdgeDensityFunction.lerp;

public final class PathDensityFunction implements DensityFunction {
    private final CavePath cavePath;
    private final double maxInfluenceRadius;
    private final double caveMainInfluenceRadius;
    private final EdgeDensityFunction edgeDensityFunction;

    private final SimplexNoiseGenerator noiseGenerator;
    private final float noiseScale;
    private final double globalNoiseFactor;
    private final double globalNoiseMagnitude;
    private final double maxRadiusSq;

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
        this.maxRadiusSq = this.maxInfluenceRadius * this.maxInfluenceRadius;
        this.edgeDensityFunction = edgeDensityFunction;

        this.noiseGenerator = new SimplexNoiseGenerator(42);
        this.noiseScale = 0.01f;
        this.globalNoiseMagnitude = 1.0;
        this.globalNoiseFactor = 1.00;
    }

    @Override
    public float apply(final Vector3 position, final Temporaries tmp) {
        this.cavePath.getNodesWithin(position,
                                     this.maxInfluenceRadius,
                                     tmp.foundNodes,
                                     tmp.nodeQueue);
        final var nodes = tmp.foundNodes;
        final var nodesArray = nodes.getBackingArray();

        var summedWeights = 0.0;
        var nContributions = 0;
        var weightedTotal = 0.0;
        var minDistance = Double.MAX_VALUE;
        var summedFloorness = 0.0;
        final var edgeResult = tmp.edgeResult;

        // Fetch temporaries here to avoid allocations in the loop
        final var tmpEdgeResult = tmp.tmpEdgeResult;
        final var tmpResult = tmp.tmpResult;
        for (var i = 0; i < nodes.getCount(); ++i) {
            final var nodeIndex = nodesArray[i];
            edgeResult.clear(false);

            // XXX: Change back to arrays if branching caves are implemented
            // final int previous = this.cavePath.getPreviousFor(nodeIndex);
            final int previous = nodeIndex - 1;
            if (previous != -1) {
                calculateEdge(edgeResult, previous, nodeIndex, position, tmpResult, tmpEdgeResult);
            }
            // XXX: Change back to arrays if branching caves are implemented
            //for (final int n : this.cavePath.getNextFor(nodeIndex)) {
            final var n = nodeIndex + 1;
            if (n < this.cavePath.getNodeCount()) {
                calculateEdge(edgeResult, nodeIndex, n, position, tmpResult, tmpEdgeResult);
            }

            if (edgeResult.hasContribution()) {
                final var weight = edgeResult.getWeight();
                weightedTotal += weight * edgeResult.getValue();
                summedFloorness += weight * edgeResult.getFloorness();
                minDistance = minDistance < edgeResult.getDistance() ? minDistance : edgeResult.getDistance();

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
        final var globalNoise = -getGlobalNoise(position, tmpResult) * globalNoiseMultiplier;
        final var globalDensity = lerp(weightedAverage, globalNoise, this.globalNoiseFactor * distanceAlpha);

        // Ensures that walls are solid after max radius
        final var fadeToSolidAlpha = minDistance / this.maxInfluenceRadius;
        final var clampedDensity = lerp(globalDensity, 0.0, fadeToSolidAlpha > 1.0 ? 1.0 : fadeToSolidAlpha);
        return (float) Math.max(0.0, Math.min(1.0, 1.0 + clampedDensity));
    }

    private double getGlobalNoise(final Vector3 pos, final Vector3 tmpResult) {
        return this.noiseGenerator.evaluate(pos.mul(this.noiseScale, tmpResult)) * this.globalNoiseMagnitude;
    }

    private void calculateEdge(
            final NodeContribution result,
            final int indexA,
            final int indexB,
            final Vector3 position,
            final Vector3 tmpResult,
            final NodeContribution tmpEdgeResult
    ) {
        final var nodeA = PathDensityFunction.this.cavePath.get(indexA);
        final var nodeB = PathDensityFunction.this.cavePath.get(indexB);
        final var closest = LineSegment.closestPoint(nodeA, nodeB, position, tmpResult);

        final var distanceSq = closest.distanceSq(position);
        if (distanceSq > this.maxRadiusSq) {
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
        final var negWeightSq = distanceSq / this.maxRadiusSq;
        final var weightSq = 1.0 - negWeightSq;

        final var contribution = this.edgeDensityFunction.apply(position, closest, distanceSq, tmpEdgeResult);
        if (contribution.hasContribution()) {
            result.combine(contribution, weightSq);
        }
    }
}
