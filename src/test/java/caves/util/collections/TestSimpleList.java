package caves.util.collections;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class TestSimpleList {
    @Test
    void addingWorks() {
        final var list = new SimpleList<>(1);
        final var expected = new Object();
        list.add(expected);

        final var actual = list.get(0);
        assertEquals(expected, actual);
    }

    @Test
    void addingWorksWhenCapacityIsZero() {
        final var list = new SimpleList<>(0);
        final var expected = new Object();
        list.add(expected);

        final var actual = list.get(0);
        assertEquals(expected, actual);
    }

    @Test
    void addingWorksWhenResizeOccurs() {
        final var list = new SimpleList<>(10);
        final var expected = new Object[100];
        for (int i = 0; i < 100; i++) {
            expected[i] = new Object();
            list.add(expected[i]);
        }

        assertAll(IntStream.range(0, expected.length)
                           .mapToObj(i -> () -> assertEquals(expected[i], list.get(i))));
    }

    @Test
    void gettingOutOfBoundsIndexThrows() {
        final var list = new SimpleList<>();
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());

        assertThrows(IndexOutOfBoundsException.class, () -> list.get(10));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-5));
    }

    @Test
    void gettingOutOfBoundsIndexThrows_emptyList() {
        final var list = new SimpleList<>();
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(10));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-5));
    }

    @Test
    void collectionIsEmptyAfterClear() {
        final var list = new SimpleList<>();
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());

        list.clear();
        assertEquals(0, list.size());
    }
}
