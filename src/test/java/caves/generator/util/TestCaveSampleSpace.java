package caves.generator.util;

import caves.generator.CavePath;
import caves.generator.CaveSampleSpace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestCaveSampleSpace {
    CavePath cave;

    @BeforeEach
    void beforeEach() {
        cave = new CavePath();
        cave.addNode(new Vector3(-4.0f, -8.0f, 4.7f));
        cave.addNode(new Vector3(6.0f, -0.724f, 8.34f));
    }

    @Test
    void sampleSpaceHasExpectedMinMax() {
        final var space = new CaveSampleSpace(cave, 1.0f, 0.25f, (p, v) -> 1.0f);
        assertAll(() -> assertEquals(new Vector3(-4.0f, -8.0f, 4.7f), space.getMin()),
                  () -> assertEquals(new Vector3(6.0f, -0.724f, 8.34f), space.getMax()));
    }

    @Test
    void sampleSpaceHasExpectedBounds() {
        final var space = new CaveSampleSpace(cave, 1.0f, 0.25f, (p, v) -> 1.0f);
        assertAll(() -> assertEquals(10.0f + 2.0f, space.getSizeX()),
                  () -> assertEquals(7.276f + 2.0f, space.getSizeY()),
                  () -> assertEquals(3.64f + 2.0f, space.getSizeZ()));
    }

    @Test
    void sampleSpaceHasExpectedCounts() {
        final var space = new CaveSampleSpace(cave, 1.0f, 0.25f, (p, v) -> 1.0f);
        assertAll(() -> assertEquals(Math.floor((10.0f + 2.0f) / 0.25f), space.getCountX(), 0.5),
                  () -> assertEquals(Math.floor((7.276f + 2.0f) / 0.25f), space.getCountY(), 0.5),
                  () -> assertEquals(Math.floor((3.64f + 2.0f) / 0.25f), space.getCountZ(), 0.5));
    }

    @Test
    @SuppressWarnings("unchecked")
    void densityFunctionIsCalledOnceForEverySample() {
        final var mockDensity = mock(BiFunction.class);
        when(mockDensity.apply(any(), any())).thenReturn(1.0f);

        final var space = new CaveSampleSpace(cave, 1.0f, 0.25f, mockDensity);
        final var sampleCount = space.getSize();
        verify(mockDensity, times(sampleCount)).apply(eq(cave), any());
    }

    @Test
    void samplePositionsAreCorrect() {
        final var index = 12;
        final var expected = new Vector3(-2f, -9f, 3.7f);

        final var space = new CaveSampleSpace(cave, 1.0f, 0.25f, (p, v) -> 1.0f);
        final var pos = space.getPos(index);
        assertAll(() -> assertEquals(expected.getX(), pos.getX(), 0.001),
                  () -> assertEquals(expected.getY(), pos.getY(), 0.001),
                  () -> assertEquals(expected.getZ(), pos.getZ(), 0.001));
    }

    @Test
    void sampleDensitiesAreFromTheDensityFunction() {
        final var space = new CaveSampleSpace(cave, 1.0f, 0.25f, (p, v) -> 42.0f);
        assertEquals(42.0f, space.getDensity(12));
        assertEquals(42.0f, space.getDensity(21));
        assertEquals(42.0f, space.getDensity(123));
    }
}