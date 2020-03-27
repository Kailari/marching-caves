package caves.generator.density;

import caves.util.math.IntVector3;
import caves.util.math.Vector3;

public final class SimplexNoiseGenerator {
    private static final float STRETCH = 1.0f / 3.0f;    // (    sqrt(3 + 1) - 1) / 3    = 1 / 3
    private static final float SQUISH = 1.0f / 6.0f;     // (1 / sqrt(3 + 1) - 1) / 3    = 1 / 6
    private static final Vector3[] GRADIENTS = {
            new Vector3(1, 1, 0), new Vector3(-1, 1, 0), new Vector3(1, -1, 0), new Vector3(-1, -1, 0),
            new Vector3(1, 0, 1), new Vector3(-1, 0, 1), new Vector3(1, 0, -1), new Vector3(-1, 0, -1),
            new Vector3(0, 1, 1), new Vector3(0, -1, 1), new Vector3(0, 1, -1), new Vector3(0, -1, -1)
    };

    private static final Vector3 D1_SQUISH = new Vector3(SQUISH, SQUISH, SQUISH);
    private static final Vector3 D2_SQUISH = new Vector3(SQUISH, SQUISH, SQUISH).mul(2.0f);
    private static final Vector3 D3_SQUISH = new Vector3(SQUISH, SQUISH, SQUISH).mul(3.0f)
                                                                                .sub(1, 1, 1);
    /** Permutations. */
    private final int[] p = new int[512];
    /** Permutations modulo 12. */
    private final int[] pMod12 = new int[512]; // Optimization: Avoid calculating lots of "x % 12"

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

        for (var i = 256; i < 512; i++) {
            this.p[i] = this.p[i - 256];
            this.pMod12[i] = (this.p[i] % 12);
        }
    }

    private static float contribution(final int gi, final Vector3 pos) {
        final var xx = pos.getX() * pos.getX();
        final var yy = pos.getY() * pos.getY();
        final var zz = pos.getZ() * pos.getZ();
        final var t = 0.6f - xx - yy - zz;
        final var tt = t * t;
        return t < 0
                ? 0.0f
                : tt * tt * GRADIENTS[gi].dot(pos);
    }

    private static int fastFloor(final float x) {
        final int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    /**
     * Evaluates noise value for the given position.
     *
     * @param position position to evaluate noise for
     *
     * @return noise value
     */
    public float evaluate(final Vector3 position) {
        // Skew the input space to determine which simplex we are in
        final var skewOffset = (position.getX() + position.getY() + position.getZ()) * STRETCH;
        // Note:    Values can be negative, so we cannot just cut the decimals by casting to int.
        //          Trying to do so produces odd looking artifacts. On the other hand, `Math.floor`
        //          is horrendously slow as it has to be well behaved in all weird edge-cases. Thus,
        //          use "simplified" custom floor implementation.
        final var skewedOrigin = new IntVector3(fastFloor(position.getX() + skewOffset),
                                                fastFloor(position.getY() + skewOffset),
                                                fastFloor(position.getZ() + skewOffset));

        // Squish (Un-skew) simplex origin back to regular coordinate space for easier distance calculations
        final var squishOffset = (skewedOrigin.getX() + skewedOrigin.getY() + skewedOrigin.getZ()) * SQUISH;
        final var simplexOrigin = new Vector3(skewedOrigin).sub(squishOffset, squishOffset, squishOffset);

        // Per-component distances from simplex cell origin
        final var d0 = position.sub(simplexOrigin, new Vector3());

        // The Simplex shape is a slightly irregular tetrahedron. Determine which simplex we are in
        // and set offsets for second and third corners. The last corner can always be calculated
        // from the origin using a constant offset (1, 1, 1).
        final var offs1 = new IntVector3();
        final var offs2 = new IntVector3();
        if (d0.getX() >= d0.getY()) {
            if (d0.getY() >= d0.getZ()) {
                // X > Y > Z
                offs1.set(1, 0, 0);
                offs2.set(1, 1, 0);
            } else if (d0.getX() >= d0.getZ()) {
                // X > Z > Y
                offs1.set(1, 0, 0);
                offs2.set(1, 0, 1);
            } else {
                // Z > X > Y
                offs1.set(0, 0, 1);
                offs2.set(1, 0, 1);
            }
        } else {
            // X < Y
            if (d0.getY() < d0.getZ()) {
                // Z > Y > X
                offs1.set(0, 0, 1);
                offs2.set(0, 1, 1);
            } else if (d0.getX() < d0.getZ()) {
                // Y > Z > X
                offs1.set(0, 1, 0);
                offs2.set(0, 1, 1);
            } else {
                // Y > X > Z
                offs1.set(0, 1, 0);
                offs2.set(1, 1, 0);
            }
        }

        // Calculate distances for the other three corners.
        final var d1 = d0.add(D1_SQUISH, new Vector3()).sub(offs1.getX(), offs1.getY(), offs1.getZ());
        final var d2 = d0.add(D2_SQUISH, new Vector3()).sub(offs2.getX(), offs2.getY(), offs2.getZ());
        final var d3 = d0.add(D3_SQUISH, new Vector3());

        // Calculate hashed gradient indices for the corners
        final int wrapX = skewedOrigin.getX() & 255;
        final int wrapY = skewedOrigin.getY() & 255;
        final int wrapZ = skewedOrigin.getZ() & 255;
        final var index0 = wrapX + this.p[wrapY + this.p[wrapZ]];
        final var index1 = wrapX + offs1.getX() + this.p[wrapY + offs1.getY() + this.p[wrapZ + offs1.getZ()]];
        final var index2 = wrapX + offs2.getX() + this.p[wrapY + offs2.getY() + this.p[wrapZ + offs2.getZ()]];
        final var index3 = wrapX + 1 + this.p[wrapY + 1 + this.p[wrapZ + 1]];
        final int gradientIndex0 = this.pMod12[index0];
        final int gradientIndex1 = this.pMod12[index1];
        final int gradientIndex2 = this.pMod12[index2];
        final int gradientIndex3 = this.pMod12[index3];

        final var contribution0 = contribution(gradientIndex0, d0);
        final var contribution1 = contribution(gradientIndex1, d1);
        final var contribution2 = contribution(gradientIndex2, d2);
        final var contribution3 = contribution(gradientIndex3, d3);

        return 32.0f * (contribution0 + contribution1 + contribution2 + contribution3);
    }
}
