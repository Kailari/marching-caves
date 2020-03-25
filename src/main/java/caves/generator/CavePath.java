package caves.generator;

import caves.util.math.Vector3;

import java.util.ArrayList;
import java.util.List;

public final class CavePath {
    private final List<Vector3> nodes;

    /**
     * Gets all nodes on the path, ordered from path start to the end.
     *
     * @return all nodes on the path
     */
    public Vector3[] getNodesOrdered() {
        return this.nodes.toArray(Vector3[]::new);
    }

    /**
     * Gets the average position of all path nodes.
     *
     * @return the average position
     */
    public Vector3 getAveragePosition() {
        return this.nodes.stream()
                         .reduce((a, b) -> a.add(b, new Vector3()))
                         .map(sum -> sum.mul(1.0f / this.nodes.size(), sum))
                         .orElseThrow();
    }

    /**
     * Constructs a new empty path.
     */
    public CavePath() {
        this.nodes = new ArrayList<>();
    }

    /**
     * Adds a node to the path. The node is added to the end of the path.
     *
     * @param node the node to append
     */
    public void addNode(final Vector3 node) {
        this.nodes.add(node);
    }

    /**
     * Finds the point on the path that is closest to the arbitrary specified point in space.
     *
     * @param position the arbitrary point in space
     *
     * @return the point on path, closest to the specified position
     */
    public Vector3 closestPoint(final Vector3 position) {
        float minDistanceSq = Float.POSITIVE_INFINITY;
        Vector3 closest = new Vector3();
        for (var i = 0; i < this.nodes.size() - 1; ++i) {
            final var nodeA = this.nodes.get(i);
            final var nodeB = this.nodes.get(i + 1);

            final var ab = nodeB.sub(nodeA, new Vector3()); // The segment AB

            final var ap = position.sub(nodeA, new Vector3()); // A -> pos
            final var bp = position.sub(nodeB, new Vector3()); // B -> pos

            // We can only calculate the perpendicular distance if the projected point from pos to
            // AB is actually on the segment. Thus, need to check here that it is not past either
            // endpoint before calculating the distance.
            final Vector3 closestOnSegment;
            if (ap.dot(ab) <= 0.0) {
                // pos is past the segment towards A, thus the closest point is A
                closestOnSegment = nodeA;
            } else if (bp.dot(ab) >= 0.0) {
                // pos is past the segment towards B, thus the closest point is B
                closestOnSegment = nodeB;
            } else {
                // Project the pos to the direction vector AB
                final var d = ab.normalize(new Vector3());
                final var t = ap.dot(d);
                closestOnSegment = nodeA.add(d.mul(t), d);
            }

            final var distanceSq = closestOnSegment.distanceSq(position);
            if (distanceSq < minDistanceSq) {
                minDistanceSq = distanceSq;
                closest = closestOnSegment;
            }
        }

        return closest;
    }
}
