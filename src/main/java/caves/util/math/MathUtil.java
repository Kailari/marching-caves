package caves.util.math;

public final class MathUtil {
    private MathUtil() {
    }

    /**
     * Calculates floor of the given value. Fast.
     *
     * @param x the value
     *
     * @return the floor
     */
    public static int fastFloor(final float x) {
        final int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }
}
