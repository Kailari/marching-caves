package caves.generator.density;

import caves.util.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSimplexNoiseGenerator {
    @Test
    void evaluateProducesValuesDistributedOnTheWholeRange() {
        var hasVeryLowValues = false;
        var hasLowValues = false;
        var hasMidValues = false;
        var hasHighValues = false;
        var hasVeryHighValues = false;
        final var generator = new SimplexNoiseGenerator(42424242L);

        final int n = 1_000_000;
        final var pos = new Vector3();
        final var distance = 0.001f;
        for (int i = 0; i < n; ++i) {
            final var value = generator.evaluate(pos.set(i * distance, 0, 0));
            if (value < -0.6) {
                hasVeryLowValues = true;
            } else if (value < -0.3) {
                hasLowValues = true;
            } else if (value < 0.3) {
                hasMidValues = true;
            } else if (value < 0.6) {
                hasHighValues = true;
            } else {
                hasVeryHighValues = true;
            }
        }

        assertTrue(hasVeryLowValues);
        assertTrue(hasLowValues);
        assertTrue(hasMidValues);
        assertTrue(hasHighValues);
        assertTrue(hasVeryHighValues);
    }

    @Test
    void evaluateDoesNotProduceOutOfBoundsValues() {
        var negativeOOB = false;
        var positiveOOB = false;
        final var generator = new SimplexNoiseGenerator(42424242L);

        final int n = 1_000_000;
        final var pos = new Vector3();
        final var distance = 0.001f;
        for (int i = 0; i < n; ++i) {
            final var value = generator.evaluate(pos.set(i * distance, i * 2 * distance, i * 3 * distance));
            if (value < -1.0) {
                negativeOOB = true;
                break;
            } else if (value > 1.0) {
                positiveOOB = true;
                break;
            }
        }

        assertFalse(negativeOOB);
        assertFalse(positiveOOB);
    }

    @Test
    void evaluateRunsRelativelyFast() {
        final var maxSeconds = 1;
        final var generator = new SimplexNoiseGenerator(42424242L);

        final var start = System.nanoTime();
        final var n = 10_000_000;
        final var pos = new Vector3();
        final var distance = 0.001f;
        for (int i = 0; i < n; ++i) {
            final var value = generator.evaluate(pos.set(i * distance, i * 2 * distance, i * 3 * distance));
        }

        final var elapsed = System.nanoTime() - start;
        assertTrue(elapsed < 1_000_000_000 * maxSeconds,
                   String.format("Evaluating %d values took too long. Expected %.4f seconds, took %.4f seconds",
                                 n,
                                 (double) maxSeconds,
                                 elapsed / 1_000_000_000.0));
    }
}
