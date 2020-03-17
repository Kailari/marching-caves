package caves.generator;

import caves.generator.util.Vector3;

import java.util.Random;

public final class PathGenerator {
    public CavePath generate(
            final Vector3 start,
            final int length,
            final float nodeSpacing,
            final long seed
    ) {
        final var path = new CavePath();

        final var random = new Random(seed);

        var previous = new Vector3(start);
        final var sum = new Vector3(0, 0, 0);
        for (var i = 0; i < length; i++) {
            final var x = (random.nextFloat() * 2.0f - 1.0f) * nodeSpacing;
            final var y = (random.nextFloat() * 2.0f - 1.0f) * nodeSpacing;
            final var z = (random.nextFloat() * 2.0f - 1.0f) * nodeSpacing;
            final var next = previous.add(x, y, z, new Vector3());
            path.addNode(next);
            sum.add(next, sum);

            previous = next;
        }

        final var middle = new Vector3(sum.getX() / length,
                                       sum.getY() / length,
                                       sum.getZ() / length);
        path.forEach(vec -> vec.sub(middle, vec));
        return path;
    }
}
