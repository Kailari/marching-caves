package caves.generator.density;

import caves.generator.CavePath;
import caves.util.math.LineSegment;
import caves.util.math.Vector3;

import java.util.function.Function;

public final class PathDensityFunction implements Function<Vector3, Float> {
    private final CavePath cavePath;
    private final double maxInfluenceRadius;
    private final EdgeDensityFunction edgeDensityFunction;

    private final Vector3 tmpResult = new Vector3();

    /**
     * Creates a new density function for a cave path. The function calculates point densities as
     * sum of nearby path edges' densities.
     *
     * @param cavePath            contributing path
     * @param maxInfluenceRadius  maximum node influence radius
     * @param edgeDensityFunction the density function to use for calculating edge density
     *                            contributions
     */
    public PathDensityFunction(
            final CavePath cavePath,
            final double maxInfluenceRadius,
            final EdgeDensityFunction edgeDensityFunction
    ) {
        this.cavePath = cavePath;
        this.maxInfluenceRadius = maxInfluenceRadius;
        this.edgeDensityFunction = edgeDensityFunction;
    }

    @Override
    public Float apply(final Vector3 position) {
        final var nodes = this.cavePath.getNodesWithin(position, this.maxInfluenceRadius);

        final var contributions = new double[nodes.size() * this.cavePath.getSplittingLimit()];
        final var weights = new double[nodes.size() * this.cavePath.getSplittingLimit()];
        var summedWeights = 0.0;
        var nContributions = 0;
        final var edgeAverage = new WeightedAverage();
        for (final var nodeIndex : nodes) {
            edgeAverage.totalWeight = 0.0;
            edgeAverage.weightedSum = 0.0;
            edgeAverage.maximumWeight = 0.0;
            edgeAverage.hasContribution = false;

            final int previous = this.cavePath.getPreviousFor(nodeIndex);
            if (previous != -1) {
                add(edgeAverage, previous, nodeIndex, position);
            }
            for (final int n : this.cavePath.getNextFor(nodeIndex)) {
                add(edgeAverage, nodeIndex, n, position);
            }

            if (edgeAverage.hasContribution) {
                final var weight = edgeAverage.maximumWeight;
                contributions[nContributions] = edgeAverage.calculate();
                weights[nContributions] = weight;
                summedWeights += weight;
                nContributions++;
            }
        }

        if (nContributions == 0) {
            return 1.0f;
        }

        var weightedTotal = 0.0;
        for (int i = 0; i < nContributions; ++i) {
            weightedTotal += weights[i] * contributions[i];
        }
        final var weightedAverage = weightedTotal / summedWeights;
        return (float) Math.max(0.0, Math.min(1.0, 1.0 + weightedAverage));
    }

    void add(
            final WeightedAverage avg,
            final int indexA,
            final int indexB,
            final Vector3 position
    ) {
        final var maxRadiusSq = this.maxInfluenceRadius * this.maxInfluenceRadius;

        assert indexA != -1 || indexB != -1 : "One of the nodes must exist!";

        final var nodeA = PathDensityFunction.this.cavePath.get(indexA);
        final var nodeB = PathDensityFunction.this.cavePath.get(indexB);
        final var closest = LineSegment.closestPoint(nodeA, nodeB, position, this.tmpResult);

        final var distanceSq = closest.distanceSq(position);
        if (distanceSq > maxRadiusSq) {
            return;
        }

        final var value = this.edgeDensityFunction.apply(nodeA,
                                                         nodeB,
                                                         position,
                                                         closest,
                                                         distanceSq);

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
        var weightSq = 1.0 - Math.min(1.0, distanceSq / maxRadiusSq);

        // HACK:    Increase the exponent to *very high* value to reduce the further away
        //          contributors' weights to zero.
        weightSq *= weightSq;
        weightSq *= weightSq;
        weightSq *= weightSq;
        weightSq *= weightSq;

        avg.weightedSum += weightSq * value;
        avg.totalWeight += weightSq;
        avg.maximumWeight = Math.max(weightSq, avg.maximumWeight);
        avg.hasContribution = true;
    }

    private static final class WeightedAverage {
        private double totalWeight;
        private double weightedSum;
        private double maximumWeight;

        private boolean hasContribution;

        WeightedAverage() {
            this.totalWeight = 0.0;
            this.weightedSum = 0.0;
            this.maximumWeight = 0.0;
            this.hasContribution = false;
        }

        public double calculate() {
            return this.weightedSum / this.totalWeight;
        }
    }
}
