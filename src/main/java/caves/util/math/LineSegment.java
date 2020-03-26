package caves.util.math;

public final class LineSegment {
    private LineSegment() {
    }

    /**
     * Finds the point on the line segment defined by nodes A and B, which is the closest point to
     * the specified point in space.
     *
     * @param position the arbitrary point in space
     * @param nodeA    the start of the line segment
     * @param nodeB    the end of the line segment
     *
     * @return the point on path, closest to the specified position
     */
    public static Vector3 closestPoint(
            final Vector3 nodeA,
            final Vector3 nodeB,
            final Vector3 position
    ) {
        final var ab = nodeB.sub(nodeA, new Vector3()); // The segment AB

        final var ap = position.sub(nodeA, new Vector3()); // A -> pos
        final var bp = position.sub(nodeB, new Vector3()); // B -> pos

        // We can only calculate the perpendicular distance if the projected point from pos to
        // AB is actually on the segment. Thus, need to check here that it is not past either
        // endpoint before calculating the distance.
        if (ap.dot(ab) <= 0.0) {
            // pos is past the segment towards A, thus the closest point is A
            return nodeA;
        } else if (bp.dot(ab) >= 0.0) {
            // pos is past the segment towards B, thus the closest point is B
            return nodeB;
        } else {
            // Project the pos to the direction vector AB
            final var d = ab.normalize(new Vector3());
            final var t = ap.dot(d);
            return nodeA.add(d.mul(t), d);
        }
    }
}
