package caves.generator.density;

import caves.generator.CavePath;
import caves.util.math.LineSegment;
import caves.util.math.Vector3;

import java.util.function.Function;

public final class PathDensityFunction implements Function<Vector3, Float> {
    private final CavePath cavePath;
    private final double maxInfluenceRadius;
    private final EdgeDensityFunction edgeDensityFunction;

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
        final var nodes = this.cavePath.getNodesWithin(position,
                                                       this.maxInfluenceRadius + this.cavePath.getNodeSpacing());

        final var contributions = new Contribution[nodes.size() * this.cavePath.getSplittingLimit()];
        var summedWeights = 0.0;
        var nContributions = 0;
        for (final var nodeIndex : nodes) {
            final var edgeAverage = new WeightedAverage();

            this.cavePath.getPreviousFor(nodeIndex).ifPresent(p -> edgeAverage.add(p, nodeIndex, position));
            this.cavePath.getNextFor(nodeIndex).forEach(n -> edgeAverage.add(nodeIndex, n, position));

            if (edgeAverage.hasContribution) {
                final var weight = edgeAverage.averageWeight();
                summedWeights += weight;
                contributions[nContributions] = new Contribution(weight, edgeAverage.calculate());
                nContributions++;
            }
        }

        if (nContributions == 0) {
            return 1.0f;
        }

        var weightedTotal = 0.0;
        for (int i = 0; i < nContributions; i++) {
            weightedTotal += (summedWeights - contributions[i].weight) * contributions[i].value;
        }
        final var weightedAverage = weightedTotal / summedWeights;
        return (float) Math.max(0.0, Math.min(1.0, 1.0 + weightedAverage));
    }

    private static final class Contribution {
        private final double weight;
        private final double value;

        private Contribution(final double weight, final double value) {
            this.weight = weight;
            this.value = value;
        }
    }

    private final class WeightedAverage {
        private double totalWeight;
        private double weightedSum;
        private boolean hasContribution;
        private int n;

        WeightedAverage() {
            this.totalWeight = 0.0;
            this.weightedSum = 0.0;
            this.n = 0;
            this.hasContribution = false;
        }

        void add(final int indexA, final int indexB, final Vector3 position) {
            final var maxRadiusSq =
                    PathDensityFunction.this.maxInfluenceRadius * PathDensityFunction.this.maxInfluenceRadius;

            assert indexA != -1 || indexB != -1 : "One of the nodes must exist!";

            final Vector3 nodeA;
            final Vector3 nodeB;
            final Vector3 closest;
            if (indexA == -1 || indexB == -1) {
                final var node = indexA != -1
                        ? PathDensityFunction.this.cavePath.get(indexA)
                        : PathDensityFunction.this.cavePath.get(indexB);
                nodeA = node;
                nodeB = node;
                closest = node;
            } else {
                nodeA = PathDensityFunction.this.cavePath.get(indexA);
                nodeB = PathDensityFunction.this.cavePath.get(indexB);
                closest = LineSegment.closestPoint(nodeA, nodeB, position);
            }

            final var distanceSq = closest.distanceSq(position);
            if (distanceSq > maxRadiusSq) {
                return;
            }

            final var value = PathDensityFunction.this.edgeDensityFunction.apply(nodeA,
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
            final var weightSq = 1.0 - Math.min(1.0, distanceSq / maxRadiusSq);
            this.weightedSum += weightSq * value;
            this.totalWeight += weightSq;
            this.n++;
            this.hasContribution = true;
        }

        public double calculate() {
            return this.weightedSum / this.totalWeight;
        }

        public double averageWeight() {
            return this.totalWeight / this.n;
        }
    }
}
