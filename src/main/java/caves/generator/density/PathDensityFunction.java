package caves.generator.density;

import caves.generator.CavePath;
import caves.util.math.LineSegment;
import caves.util.math.Vector3;

import java.util.ArrayList;
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
        final var nodes = this.cavePath.getNodesWithin(position, this.maxInfluenceRadius);

        final var contributions = new ArrayList<Contribution>();
        var summedWeights = 0.0;
        for (final var nodeIndex : nodes) {
            final var node = this.cavePath.get(nodeIndex);

            final var previousIndex = this.cavePath.getPreviousFor(nodeIndex);

            if (previousIndex == -1) {
                continue; // TODO
            }
            final var previous = this.cavePath.get(previousIndex);
            final var closestOnPrevious = LineSegment.closestPoint(previous, node, position);
            final var distSqToPrevClosest = closestOnPrevious.distanceSq(position);
            final var previousValue = this.edgeDensityFunction.apply(node,
                                                                     previous,
                                                                     position);

            var minDistance = Float.POSITIVE_INFINITY;
            var value = 0.0;
            for (final var nextIndex : this.cavePath.getNextFor(nodeIndex)) {
                if (nextIndex == -1) {
                    continue; // TODO
                }

                final var next = this.cavePath.get(nextIndex);
                final var closestOnNext = LineSegment.closestPoint(previous, node, position);
                final var distSqToNextClosest = closestOnNext.distanceSq(position);
                final var nextValue = this.edgeDensityFunction.apply(node,
                                                                     next,
                                                                     position);

                final var distance = Math.min(distSqToPrevClosest, distSqToNextClosest);
                if (distance < minDistance) {
                    minDistance = distance;

                    final var distTotal = distSqToPrevClosest + distSqToNextClosest;
                    final var weightedPrev = distSqToPrevClosest * previousValue;
                    final var weightedNext = distSqToNextClosest * nextValue;
                    value = (weightedPrev + weightedNext) / distTotal;
                    //value = Math.min(nextValue, previousValue);
                }
            }

            final var maxInfluenceRadiusSq = this.maxInfluenceRadius * this.maxInfluenceRadius;
            if (Float.isFinite(minDistance) && minDistance < maxInfluenceRadiusSq) {
                final var weight = 1.0 - Math.min(1.0, Math.sqrt(minDistance) / this.maxInfluenceRadius);
                contributions.add(new Contribution(weight, value));
                summedWeights += weight;
            }
        }

        if (contributions.size() == 0) {
            return 1.0f;
        }

        final var weightedTotal = contributions.stream()
                                               .mapToDouble(c -> c.weight * c.value)
                                               .sum();

        // HACK:    Clamp minimum weights to at least 0.5 to prevent contributions averaging to NaN
        //          when all weights are very small. This value could be as small as desired, but
        //          using a relatively high value tends to smooth out the areas where this is an
        //          issue (areas where overall noise contributions stretch the wall far away from
        //          the actual path).
        final var weightedAverage = weightedTotal / Math.max(0.5, summedWeights);
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
}
