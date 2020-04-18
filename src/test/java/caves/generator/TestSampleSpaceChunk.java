package caves.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestSampleSpaceChunk {
    ChunkCaveSampleSpace sampleSpace;

    @BeforeEach
    void beforeEach() {
        sampleSpace = new ChunkCaveSampleSpace(1.0f, (v) -> v.x + v.y + v.z);
    }

    @Test
    void correctSamplesPerUnitIsStored() {
        assertEquals(4.2f, new ChunkCaveSampleSpace(4.2f, v -> 0.0f).getSamplesPerUnit());
    }

    @Test
    void gettingDensityForPositiveCoordinatesWorks() {
        assertEquals(1 + 2 + 3, sampleSpace.getDensity(1, 2, 3));
    }

    @Test
    void gettingDensityForNegativeCoordinatesWorks() {
        assertEquals(-1 - 2 - 3, sampleSpace.getDensity(-1, -2, -3));
    }

    @Test
    void gettingDensityForLargePositiveCoordinatesWorks() {
        assertEquals(1000 + 2000 + 3000, sampleSpace.getDensity(1000, 2000, 3000));
    }

    @Test
    void gettingDensityForLargeNegativeCoordinatesWorks() {
        assertEquals(-1000 - 2000 - 3000, sampleSpace.getDensity(-1000, -2000, -3000));
    }

    @Test
    void markQueueReturnsTrueOnFirstCall() {
        assertTrue(sampleSpace.markQueued(1, 2, 3));
    }

    @Test
    void markQueueMayReturnTrueOnlyOnFirstAndFalseFromThenOn() {
        sampleSpace.markQueued(1, 2, 3);
        for (int i = 0; i < 10; i++) {
            assertFalse(sampleSpace.markQueued(1, 2, 3));
        }
    }
}