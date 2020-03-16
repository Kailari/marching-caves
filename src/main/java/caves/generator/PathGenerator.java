package caves.generator;

import caves.generator.util.Vector3;

import java.util.Random;

public final class PathGenerator {
    public CavePath generate(final Vector3 start, final int length, final float nodeSpacing) {
        final var path = new CavePath();

        final var random = new Random();
        var previous = new Vector3(start);
        for (var i = 0; i < length; i++) {
            final var x = (random.nextFloat() * 2.0f - 1.0f) * nodeSpacing;
            final var y = (random.nextFloat() * 2.0f - 1.0f) * nodeSpacing;
            final var z = (random.nextFloat() * 2.0f - 1.0f) * nodeSpacing;
            final var next = previous.add(x, y, z);
            path.addNode(next);

            previous = next;
        }

        return path;
    }
}
