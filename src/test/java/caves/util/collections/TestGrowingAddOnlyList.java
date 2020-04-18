package caves.util.collections;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"MismatchedQueryAndUpdateOfCollection", "ResultOfMethodCallIgnored", "ConstantConditions", "CollectionAddedToSelf"})
public class TestGrowingAddOnlyList {
    @Test
    void unsupportedOPsAreUnsupported() {
        final var list = new GrowingAddOnlyList<>(0);
        assertThrows(UnsupportedOperationException.class, () -> list.remove(new Object()));
        assertThrows(UnsupportedOperationException.class, () -> list.remove(0));
        assertThrows(UnsupportedOperationException.class, () -> list.removeAll(List.of()));
        assertThrows(UnsupportedOperationException.class, () -> list.addAll(0, List.of()));
        assertThrows(UnsupportedOperationException.class, () -> list.add(0, new Object()));
        assertThrows(UnsupportedOperationException.class, list::listIterator);
        assertThrows(UnsupportedOperationException.class, () -> list.listIterator(1));
        assertThrows(UnsupportedOperationException.class, () -> list.subList(0, 1));
    }

    @Test
    void emptyListIsEmpty() {
        final var list = new GrowingAddOnlyList<>(0);
        assertTrue(list.isEmpty());
    }

    @Test
    void addingWorks() {
        final var list = new GrowingAddOnlyList<>(1);
        final var expected = new Object();
        list.add(expected);

        final var actual = list.get(0);
        assertEquals(expected, actual);
    }

    @Test
    void addingWorksWhenCapacityIsZero() {
        final var list = new GrowingAddOnlyList<>(0);
        final var expected = new Object();
        list.add(expected);

        final var actual = list.get(0);
        assertEquals(expected, actual);
    }

    @Test
    void addingWorksWhenResizeOccurs() {
        final var list = new GrowingAddOnlyList<>(10);
        final var expected = new Object[100];
        for (int i = 0; i < 100; i++) {
            expected[i] = new Object();
            list.add(expected[i]);
        }

        assertAll(IntStream.range(0, expected.length)
                           .mapToObj(i -> () -> assertEquals(expected[i], list.get(i))));
    }

    @Test
    void nonEmptyListIsNotEmpty() {
        final var list = new GrowingAddOnlyList<>(1);
        list.add(new Object());
        assertFalse(list.isEmpty());
    }

    @Test
    void emptyListWithCapacityIsEmpty() {
        final var list = new GrowingAddOnlyList<>(32);
        assertTrue(list.isEmpty());
    }

    @Test
    void gettingOutOfBoundsIndexThrows() {
        final var list = new GrowingAddOnlyList<>();
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
        final var list = new GrowingAddOnlyList<>();
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(10));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-5));
    }

    @Test
    void containsReturnsTrueIfElementIsInTheCollection() {
        final var list = new GrowingAddOnlyList<>();
        list.add(new Object());
        list.add(new Object());
        final var e = new Object();
        list.add(e);
        list.add(new Object());
        list.add(new Object());

        assertTrue(list.contains(e));
    }

    @Test
    void containsReturnsFalseIfElementIsNotInTheCollection() {
        final var list = new GrowingAddOnlyList<>();
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());
        final var e = Integer.valueOf(42);

        assertFalse(list.contains(e));
    }

    @Test
    void containsAllReturnsTrueIfElementsAreInTheCollection() {
        final var list = new GrowingAddOnlyList<>();
        list.add(new Object());
        list.add(new Object());
        final var e = new Object();
        final var e2 = new Object();
        list.add(e);
        list.add(e2);
        list.add(new Object());
        list.add(new Object());

        assertTrue(list.containsAll(List.of(e, e2)));
    }

    @Test
    void containsAllReturnsFalseIfElementIsNotInTheCollection() {
        final var list = new GrowingAddOnlyList<>();
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());
        final var e = Integer.valueOf(42);
        final var e2 = Float.valueOf(42.0f);

        assertFalse(list.containsAll(List.of(e, e2)));
    }

    @Test
    void collectionIsEmptyAfterClear() {
        final var list = new GrowingAddOnlyList<>();
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());
        list.add(new Object());

        list.clear();
        assertTrue(list.isEmpty());
    }

    @Test
    void addAllAddsAll() {
        final var list = new GrowingAddOnlyList<>();
        list.addAll(List.of(new Object(), new Object(), new Object(), new Object(), new Object()));
        assertEquals(5, list.size());
    }

    @Test
    void retainAllRetainsAll() {
        final var list = new GrowingAddOnlyList<>();
        list.add(0);
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);

        final var retained = List.of(3, 2, 0);
        list.retainAll(retained);

        assertTrue(list.containsAll(retained));
    }

    @Test
    void retainAllRemovesAllOthers() {
        final var list = new GrowingAddOnlyList<>();
        list.add(0);
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);

        list.retainAll(List.of(3, 2, 0));

        assertFalse(list.contains(1));
        assertFalse(list.contains(4));
    }

    @Test
    void retainAllReturnsFalseIfRetainingWhenEmpty() {
        final var list = new GrowingAddOnlyList<>();
        assertFalse(list.retainAll(List.of(1, 2, 3)));
    }

    @Test
    void retainAllReturnsFalseIfRetainingSelf() {
        final var list = new GrowingAddOnlyList<>();
        list.add(0);
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);

        assertFalse(list.retainAll(list));
    }

    @Test
    void retainAllWorksCorrectWhenRetainingSameSizeLists() {
        final var list = new GrowingAddOnlyList<>();
        list.add(0);
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);

        final var list2 = new GrowingAddOnlyList<>();
        list2.add(2);
        list2.add(3);
        list2.add(4);
        list2.add(5);
        list2.add(6);

        assertTrue(list.retainAll(list2));
        assertTrue(list.contains(2));
        assertTrue(list.contains(3));
        assertTrue(list.contains(4));
        assertEquals(3, list.size());
    }

    @Test
    void retainAllWorksCorrect() {
        final var list = new GrowingAddOnlyList<>();
        list.add(0);
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);

        final var list2 = new GrowingAddOnlyList<>();
        list2.add(2);
        list2.add(3);
        list2.add(4);
        list2.add(5);
        list2.add(6);
        list2.add(7);
        list2.add(8);

        assertTrue(list.retainAll(list2));
        assertTrue(list.contains(2));
        assertTrue(list.contains(3));
        assertTrue(list.contains(4));
        assertEquals(3, list.size());
    }

    @Test
    void setSetsTheValue() {
        final var list = new GrowingAddOnlyList<>();
        list.add(0);
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);

        list.set(2, 42);
        assertEquals(42, list.get(2));
    }

    @Test
    void indexOfReturnsTheCorrectIndex() {
        final var list = new GrowingAddOnlyList<>();
        list.add(0);
        list.add(1);
        list.add(2);
        list.add(2);
        list.add(2);

        assertEquals(2, list.indexOf(2));
    }

    @Test
    void indexOfReturnsMinusOneWhenElementDoesNotExist() {
        final var list = new GrowingAddOnlyList<>();
        list.add(2);
        list.add(2);
        list.add(2);
        list.add(3);
        list.add(4);

        assertEquals(-1, list.indexOf(42));
    }

    @Test
    void lastIndexOfReturnsTheCorrectIndex() {
        final var list = new GrowingAddOnlyList<>();
        list.add(2);
        list.add(2);
        list.add(2);
        list.add(3);
        list.add(4);

        assertEquals(2, list.lastIndexOf(2));
    }

    @Test
    void lastIndexOfReturnsMinusOneWhenElementDoesNotExist() {
        final var list = new GrowingAddOnlyList<>();
        list.add(2);
        list.add(2);
        list.add(2);
        list.add(3);
        list.add(4);

        assertEquals(-1, list.lastIndexOf(42));
    }
}
