package caves.generator;

import caves.generator.util.Vector3;

import java.util.Random;

import static caves.util.profiler.Profiler.PROFILER;

public final class PathGenerator {
    /**
     * Generates a new path using simple Random Walk.
     *
     * @param start       starting point
     * @param length      number of steps
     * @param nodeSpacing length of a single step
     * @param seed        generation PRNG seed
     *
     * @return a generated cave path
     */
    public CavePath generate(
            final Vector3 start,
            final int length,
            final float nodeSpacing,
            final long seed
    ) {
        PROFILER.log("-> With {} steps.", length);
        final var path = new CavePath();

        final var random = new Random(seed);

        var previous = new Vector3(start);
        final var sum = new Vector3(0, 0, 0);
        final var direction = new Vector3(0, 0, -1);
        path.addNode(previous);
        for (var i = 1; i < length; i++) {
            final var x = Math.fma(random.nextFloat(), 2.0f, -1.0f) * nodeSpacing;
            final var y = Math.fma(random.nextFloat(), 2.0f, -1.0f) * nodeSpacing;
            final var z = Math.fma(random.nextFloat(), 2.0f, -1.0f) * nodeSpacing;
            direction.add(x, y, z, direction);
            direction.normalize();
            direction.mul(nodeSpacing, direction);

            final var next = previous.add(direction, new Vector3());
            path.addNode(next);
            sum.add(next, sum);

            previous = next;
        }

        return path;
    }
}
