package caves.util.math;

public final class LineSegment {
    private LineSegment() {
    }

    /**
     * Finds the point on the line segment defined by nodes A and B, which is the closest point to
     * the specified point in space.
     *
     * @param a      the start of the line segment
     * @param b      the end of the line segment
     * @param p      the arbitrary point in space
     * @param result vector to hold the result
     *
     * @return the point on path, closest to the specified position
     */
    public static Vector3 closestPoint(
            final Vector3 a,
            final Vector3 b,
            final Vector3 p,
            final Vector3 result
    ) {
        final var abx = b.x - a.x;
        final var aby = b.y - a.y;
        final var abz = b.z - a.z;

        // We can only calculate the perpendicular distance if the projected point from pos to
        // AB is actually on the segment. Thus, need to check here that it is not past either
        // endpoint before calculating the distance.
        // "bp dot ab" >= 0
        if (abx * (p.x - b.x) + aby * (p.y - b.y) + abz * (p.z - b.z) >= 0.0) {
            // pos is past the segment towards B, thus the closest point is B
            return result.set(b);
        }

        final var apx = p.x - a.x;
        final var apy = p.y - a.y;
        final var apz = p.z - a.z;

        // "ap dot ab" <= 0
        if (abx * apx + aby * apy + abz * apz <= 0.0) {
            // pos is past the segment towards A, thus the closest point is A
            return result.set(a);
        }

        // Project the pos to the direction vector AB
        final var length = Math.sqrt(abx * abx + aby * aby + abz * abz);
        final var dx = abx / length;
        final var dy = aby / length;
        final var dz = abz / length;
        final var t = apx * dx + apy * dy + apz * dz;
        return a.add((float) (dx * t), (float) (dy * t), (float) (dz * t), result);
    }
}
