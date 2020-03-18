package caves.generator;

import caves.generator.util.Vector3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// TODO: Store nodes in octree for faster distance queries

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
     * Constructs a new empty path.
     */
    public CavePath() {
        this.nodes = new ArrayList<>();
    }

    public void forEach(final Consumer<Vector3> action) {
        this.nodes.forEach(action);
    }

    /**
     * Adds a node to the path. The node is added to the end of the path.
     *
     * @param node the node to append
     */
    public void addNode(final Vector3 node) {
        this.nodes.add(node);
    }

    public float distanceTo(final Vector3 pos) {
        float minDistanceSq = Float.POSITIVE_INFINITY;
        for (var i = 0; i < this.nodes.size() - 1; ++i) {
            final var nodeA = this.nodes.get(i);
            final var nodeB = this.nodes.get(i + 1);

            final var ab = nodeB.sub(nodeA, new Vector3()); // The segment AB

            final var ap = pos.sub(nodeA, new Vector3()); // A -> pos
            final var bp = pos.sub(nodeB, new Vector3()); // B -> pos

            // We can only calculate the perpendicular distance if the projected point from pos to
            // AB is actually on the segment. Thus, need to check here that it is not past either
            // endpoint before calculating the distance. If the point is past the endpoints, we can
            // use the distance to the respective node (which happens to be the length of AP or BP)
            final float distanceSq;
            if (ap.dot(ab) <= 0.0) {
                // pos is past the segment towards A, thus we cannot calculate perpendicular
                // distance so just use distance to A
                distanceSq = ap.lengthSq();
            } else if (bp.dot(ab) >= 0.0) {
                // pos is past the segment towards B, thus we cannot calculate perpendicular
                // distance so just use distance to B
                distanceSq = bp.lengthSq();
            } else {
                // Calculate perpendicular distance using cross product
                final var v = ap.cross(ab, new Vector3());

                // Calculate squared distance to avoid calls to `sqrt()`. This exploits the fact
                // that these two are equivalent in this context (distance and lengths are always
                // positive), so we can use either form:
                //      a = b / c
                //      a^2 = b^2 / c^2
                distanceSq = v.lengthSq() / ab.lengthSq();
            }

            if (distanceSq < minDistanceSq) {
                minDistanceSq = distanceSq;
            }
        }

        // Take the square root here to get the actual distance
        return (float) Math.sqrt(minDistanceSq);
    }
}
