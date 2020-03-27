package caves.generator.density;

import caves.generator.CavePath;
import caves.util.math.LineSegment;
import caves.util.math.Vector3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.Function;

public final class ContributionDensityFunction implements Function<Vector3, Float> {
    private final CavePath cavePath;
    private final double maxInfluenceRadius;
    private final EdgeDensityFunction edgeDensityFunction;

    /**
     * Creates a new contribution density function. The function calculates point densities as sum
     * of nearby density contributors' densities.
     * <p>
     * Current implementation treats all nearby nodes from the given cave path as density
     * contributors.
     *
     * @param cavePath            contributing path
     * @param maxInfluenceRadius  maximum node influence radius
     * @param edgeDensityFunction the density function to use for calculating edge density
     *                            contributions
     */
    public ContributionDensityFunction(
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
        if (true) {
            return this.edgeDensityFunction.apply(new Vector3(), new Vector3(), position);
        }

        final var nodes = this.cavePath.getNodesWithin(position, this.maxInfluenceRadius);

        // 1. Gather all contributions and distances to pos as pairs
        // 2. The distances are now inverse weights for the contributions, thus
        //  -   The final contribution is calculated as sum of contributions multiplied by
        //      distances, which is then divided by sum of weights (weighted arithmetic mean)
        final var contributions = new ArrayList<Contribution>(nodes.size());
        var summedWeights = 0.0;
        for (final var nodeIndex : nodes) {
            final var nodeSet = new HashSet<Integer>();
            nodeSet.add(this.cavePath.getPreviousFor(nodeIndex));
            nodeSet.addAll(this.cavePath.getNextFor(nodeIndex));

            var minDistance = Float.POSITIVE_INFINITY;
            var minWeight = 0.0;
            var minContribution = 0.0f;
            for (final var nextIndex : nodeSet) {
                if (nextIndex == -1) {
                    continue;
                }

                final var node = this.cavePath.get(nodeIndex);
                final var next = this.cavePath.get(nextIndex);
                final var closest = LineSegment.closestPoint(node, next, position);
                final var distanceSq = closest.distanceSq(position);
                if (distanceSq > minDistance) {
                    continue;
                }

                minDistance = distanceSq;
                minWeight = 1.0 - Math.min(1.0, Math.sqrt(distanceSq) / this.maxInfluenceRadius);
                minContribution = this.edgeDensityFunction.apply(node, next, position);
            }

            final var maxInfluenceRadiusSq = this.maxInfluenceRadius * this.maxInfluenceRadius;
            if (Float.isFinite(minDistance) && minDistance < maxInfluenceRadiusSq) {
                contributions.add(new Contribution(minWeight, minContribution));
                summedWeights += minWeight;
            }
        }

        if (contributions.size() == 0) {
            return 1.0f;
        }

        var summedWeightedContribution = 0.0;
        if (contributions.size() == 1) {
            summedWeightedContribution = contributions.get(0).value;
            summedWeights = 1.0;
        } else {
            for (final var contribution : contributions) {
                summedWeightedContribution += contribution.weight * contribution.value;
            }
        }
        final var averageContribution = summedWeightedContribution / summedWeights;

        return Math.max(0.0f, Math.min(1.0f, 1.0f + (float) averageContribution));
    }

    private static final class Contribution {
        private final double weight;
        private final float value;

        private Contribution(final double weight, final float value) {
            this.weight = weight;
            this.value = value;
        }
    }
}
