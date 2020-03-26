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
        // Construct all possible edges by creating a set of all nodes within the influence radius
        // and their adjacent (previous) nodes. We only add previous (parent) nodes as this reduces
        // the required number of set insert operations due to all nodes having at most a single
        // parent. This way, we can then generate all edge contributions by iterating all edges to
        // children from all nodes in the set.
        // TODO: Instead of iterating edge-by-edge, iterate nodes themselves
        //          -   Iterate all edges starting from that node, select the single closest point
        //              and use its contribution
        //          -   THE PATH CANNOT HAVE PER-EDGE CONTRIBUTIONS WITHOUT LINEAR INTERPOLATION
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

            if (Float.isFinite(minDistance)) {
                contributions.add(new Contribution(minWeight, minContribution));
                summedWeights += minWeight;
            }
        }

        if (contributions.size() == 0) {
            return 1.0f;
        }

        var minContribution = 1.0f;
        for (final var contribution : contributions) {
            minContribution = Math.min(minContribution, contribution.value);
        }

        return Math.max(0.0f, Math.min(1.0f, 1.0f + (float) minContribution));
    }

    private static final class Contribution {
        private final double weight;
        private final float value;

        private Contribution(
                final double weight,
                final float value
        ) {
            this.weight = weight;
            this.value = value;
        }
    }
}
