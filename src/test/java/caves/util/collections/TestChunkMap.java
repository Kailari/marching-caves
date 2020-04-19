package caves.util.collections;

import caves.generator.SampleSpaceChunk;
import org.junit.jupiter.api.Test;

import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestChunkMap {
    @Test
    void puttingAndGettingASingleValueReturnsTheSameInstance() {
        final var map = new ChunkMap(256);
        final var chunk = new SampleSpaceChunk(0, 0, 0, 0);
        map.put(1337, chunk);

        assertEquals(chunk, map.get(1337));
    }

    @Test
    void sizeIsCorrectAfterPuttingLargeNumberOfValues() {
        final var map = new ChunkMap(256);
        for (long i = 0; i < 10_000; ++i) {
            final var chunk = new SampleSpaceChunk(0, 0, 0, 0);
            map.put(1337 + i, chunk);
        }

        assertEquals(10_000, map.getSize());
    }

    @Test
    void valuesGetsListOfAllChunks() {
        final var map = new ChunkMap(256);
        for (long i = 0; i < 123; ++i) {
            final var chunk = new SampleSpaceChunk(0, 0, 0, 0);
            map.put(1337 + i, chunk);
        }

        assertEquals(123,
                     StreamSupport.stream(map.values().spliterator(), false)
                                  .count());
    }
}
