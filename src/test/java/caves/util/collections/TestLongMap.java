package caves.util.collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestLongMap {
    @Test
    void puttingAndGettingASingleValueReturnsTheSameInstance() {
        final var map = new LongMap<>(256);
        final var chunk = new Object();
        map.put(1337, chunk);

        assertEquals(chunk, map.get(1337));
    }

    @Test
    void puttingWithSameIndexReplacesThePreviousValue() {
        final var map = new LongMap<>(256);
        map.put(1337, new Object());

        final var chunk = Integer.valueOf(0);
        map.put(1337, chunk);

        assertEquals(chunk, map.get(1337));
    }

    @Test
    void puttingWithSameIndexDoesNotIncrementSize() {
        final var map = new LongMap<>(256);
        map.put(1337, new Object());

        final var chunk = new Object();
        map.put(1337, chunk);

        assertEquals(1, map.getSize());
    }

    @Test
    void sizeIsCorrectAfterPuttingLargeNumberOfValues() {
        final var map = new LongMap<>(256);
        for (long i = 0; i < 10_000; ++i) {
            final var chunk = new Object();
            map.put(1337 + i, chunk);
        }

        assertEquals(10_000, map.getSize());
    }

    @Test
    void valuesGetsListOfAllChunks() {
        final var map = new LongMap<>(256);
        for (long i = 0; i < 123; ++i) {
            final var chunk = new Object();
            map.put(1337 + i, chunk);
        }

        int count = 0;
        for (final var ignored : map.values()) {
            ++count;
        }

        assertEquals(123, count);
    }
}
