package caves.util.math;

public final class LineSegment {
    private static final Vector3 tmpAB = new Vector3();
    private static final Vector3 tmpAp = new Vector3();
    private static final Vector3 tmpBp = new Vector3();

    private LineSegment() {
    }

    /**
     * Finds the point on the line segment defined by nodes A and B, which is the closest point to
     * the specified point in space.
     *
     * @param nodeA    the start of the line segment
     * @param nodeB    the end of the line segment
     * @param position the arbitrary point in space
     * @param result   vector to hold the result
     *
     * @return the point on path, closest to the specified position
     */
    public static Vector3 closestPoint(
            final Vector3 nodeA,
            final Vector3 nodeB,
            final Vector3 position,
            final Vector3 result
    ) {
        final var ab = nodeB.sub(nodeA, tmpAB); // The segment AB

        final var ap = position.sub(nodeA, tmpAp); // A -> pos
        final var bp = position.sub(nodeB, tmpBp); // B -> pos

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
            final var d = ab.normalize();
            final var t = ap.dot(d);
            return nodeA.add(d.mul(t), result);
        }
    }
}
