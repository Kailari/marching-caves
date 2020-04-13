package caves.generator.density;

import caves.util.math.Vector3;

public final class SimplexNoiseGenerator {
    private static final float STRETCH = 1.0f / 3.0f;    // (    sqrt(3 + 1) - 1) / 3    = 1 / 3
    private static final float SQUISH = 1.0f / 6.0f;     // (1 / sqrt(3 + 1) - 1) / 3    = 1 / 6
    private static final float[][] GRADIENTS = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
    };

    private static final float D1_SQUISH = SQUISH;
    private static final float D2_SQUISH = SQUISH * 2.0f;
    private static final float D3_SQUISH = SQUISH * 3.0f - 1;

    /** Permutations. */
    private final int[] p = new int[256];
    /** Permutations modulo 12. */
    private final int[] pMod12 = new int[256]; // Optimization: Avoid calculating lots of "x % 12"

    /**
     * Creates and seeds a new simplex noise generator.
     *
     * @param seed the seed number
     */
    public SimplexNoiseGenerator(final long seed) {
        final var source = new short[256];
        for (short i = 0; i < 256; i++) {
            source[i] = i;
        }

        long actualSeed = seed * 6364136223846793005L + 1442695040888963407L;
        actualSeed = actualSeed * 6364136223846793005L + 1442695040888963407L;
        actualSeed = actualSeed * 6364136223846793005L + 1442695040888963407L;

        for (var i = 255; i >= 0; i--) {
            actualSeed = actualSeed * 6364136223846793005L + 1442695040888963407L;
            int r = (int) ((actualSeed + 31) % (i + 1));
            if (r < 0) {
                r += (i + 1);
            }

            this.p[i] = source[r];
            this.pMod12[i] = (this.p[i] % 12);
            source[r] = source[i];
        }
    }

    private static float contribution(final int gi, final float x, final float y, final float z) {
        final var xx = x * x;
        final var yy = y * y;
        final var zz = z * z;
        final var t = 0.6f - xx - yy - zz;
        final var tt = t * t;
        return t < 0
                ? 0.0f
                : tt * tt * (GRADIENTS[gi][0] * x + GRADIENTS[gi][1] * y + GRADIENTS[gi][2] * z);
    }

    private static int fastFloor(final float x) {
        final int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    /**
     * Evaluates noise value for the given position. Does not use vector utility classes to avoid
     * all unnecessary method calls.
     *
     * @param position position to evaluate noise for
     *
     * @return noise value
     */
    public float evaluate(final Vector3 position) {
        final float x = position.getX();
        final float y = position.getY();
        final float z = position.getZ();
        // Skew the input space to determine which simplex we are in
        final float skewOffset = (x + y + z) * STRETCH;
        // Note:    Values can be negative, so we cannot just cut the decimals by casting to int.
        //          Trying to do so produces odd looking artifacts. On the other hand, `Math.floor`
        //          is horrendously slow as it has to be well behaved in all weird edge-cases. Thus,
        //          use "simplified" custom floor implementation.
        final int skewedOriginX = fastFloor(x + skewOffset);
        final int skewedOriginY = fastFloor(y + skewOffset);
        final int skewedOriginZ = fastFloor(z + skewOffset);

        // Squish (Un-skew) simplex origin back to regular coordinate space for easier distance calculations
        final float squishOffset = (skewedOriginX + skewedOriginY + skewedOriginZ) * SQUISH;
        final float simplexOriginX = skewedOriginX - squishOffset;
        final float simplexOriginY = skewedOriginY - squishOffset;
        final float simplexOriginZ = skewedOriginZ - squishOffset;

        // Per-component distances from simplex cell origin
        final float d0X = x - simplexOriginX;
        final float d0Y = y - simplexOriginY;
        final float d0Z = z - simplexOriginZ;

        // The Simplex shape is a slightly irregular tetrahedron. Determine which simplex we are in
        // and set offsets for second and third corners. The last corner can always be calculated
        // from the origin using a constant offset (1, 1, 1).
        final int offs1X;
        final int offs1Y;
        final int offs1Z;
        final int offs2X;
        final int offs2Y;
        final int offs2Z;
        if (d0X >= d0Y) {
            if (d0Y >= d0Z) {
                // X > Y > Z
                offs1X = 1;
                offs1Y = 0;
                offs1Z = 0;
                offs2X = 1;
                offs2Y = 1;
                offs2Z = 0;
            } else if (d0X >= d0Z) {
                // X > Z > Y
                offs1X = 1;
                offs1Y = 0;
                offs1Z = 0;
                offs2X = 1;
                offs2Y = 0;
                offs2Z = 1;
            } else {
                // Z > X > Y
                offs1X = 0;
                offs1Y = 0;
                offs1Z = 1;
                offs2X = 1;
                offs2Y = 0;
                offs2Z = 1;
            }
        } else {
            // X < Y
            if (d0Y < d0Z) {
                // Z > Y > X
                offs1X = 0;
                offs1Y = 0;
                offs1Z = 1;
                offs2X = 0;
                offs2Y = 1;
                offs2Z = 1;
            } else if (d0X < d0Z) {
                // Y > Z > X
                offs1X = 0;
                offs1Y = 1;
                offs1Z = 0;
                offs2X = 0;
                offs2Y = 1;
                offs2Z = 1;
            } else {
                // Y > X > Z
                offs1X = 0;
                offs1Y = 1;
                offs1Z = 0;
                offs2X = 1;
                offs2Y = 1;
                offs2Z = 0;
            }
        }

        // Calculate distances for the other three corners.
        final float d1X = d0X + D1_SQUISH - offs1X;
        final float d1Y = d0Y + D1_SQUISH - offs1Y;
        final float d1Z = d0Z + D1_SQUISH - offs1Z;
        final float d2X = d0X + D2_SQUISH - offs2X;
        final float d2Y = d0Y + D2_SQUISH - offs2Y;
        final float d2Z = d0Z + D2_SQUISH - offs2Z;
        final float d3X = d0X + D3_SQUISH;
        final float d3Y = d0Y + D3_SQUISH;
        final float d3Z = d0Z + D3_SQUISH;

        // Calculate hashed gradient indices for the corners
        final int wrapX = skewedOriginX & 0xFF;
        final int wrapY = skewedOriginY & 0xFF;
        final int wrapZ = skewedOriginZ & 0xFF;
        final int index0 = wrapX + this.p[(wrapY + this.p[wrapZ & 0xFF]) & 0xFF];
        final int index1 = wrapX + offs1X + this.p[(wrapY + offs1Y + this.p[(wrapZ + offs1Z) & 0xFF]) & 0xFF];
        final int index2 = wrapX + offs2X + this.p[(wrapY + offs2Y + this.p[(wrapZ + offs2Z) & 0xFF]) & 0xFF];
        final int index3 = wrapX + 1 + this.p[(wrapY + 1 + this.p[(wrapZ + 1) & 0xFF]) & 0xFF];
        final int gradientIndex0 = this.pMod12[index0 & 0xFF];
        final int gradientIndex1 = this.pMod12[index1 & 0xFF];
        final int gradientIndex2 = this.pMod12[index2 & 0xFF];
        final int gradientIndex3 = this.pMod12[index3 & 0xFF];

        final float contribution0 = contribution(gradientIndex0, d0X, d0Y, d0Z);
        final float contribution1 = contribution(gradientIndex1, d1X, d1Y, d1Z);
        final float contribution2 = contribution(gradientIndex2, d2X, d2Y, d2Z);
        final float contribution3 = contribution(gradientIndex3, d3X, d3Y, d3Z);

        return 32.0f * (contribution0 + contribution1 + contribution2 + contribution3);
    }
}
