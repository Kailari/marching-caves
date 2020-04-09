package caves.util.collections;

import java.lang.reflect.Array;
import java.util.*;

/**
 * Collection which supports only adding elements.
 *
 * @param <T> element type
 */
@SuppressWarnings("unchecked")
public final class GrowingAddOnlyList<T> implements List<T> {
    private final Class<T> elementClass;
    private int count;
    private T[] elements;

    @Override
    public boolean isEmpty() {
        return this.count == 0;
    }

    /**
     * Creates a new collection with the given element type.
     *
     * @param elementClass type for the elements
     */
    public GrowingAddOnlyList(final Class<T> elementClass) {
        this(elementClass, 32);
    }

    /**
     * Creates a new collection with the given element type and initial capacity.
     *
     * @param elementClass type for the elements
     * @param capacity     initial capacity to allocate for the collection
     */
    public GrowingAddOnlyList(final Class<T> elementClass, final int capacity) {
        this.elementClass = elementClass;
        this.elements = (T[]) Array.newInstance(this.elementClass, capacity);
    }

    @Override
    public int size() {
        return this.count;
    }

    @Override
    public boolean contains(final Object o) {
        for (var i = 0; i < this.count; ++i) {
            if (this.elements[i].equals(o)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private int index = -1;

            @Override
            public boolean hasNext() {
                return this.index + 1 < GrowingAddOnlyList.this.count;
            }

            @Override
            public T next() {
                this.index++;
                if (this.index >= GrowingAddOnlyList.this.count) {
                    throw new NoSuchElementException();
                }

                return GrowingAddOnlyList.this.elements[this.index];
            }
        };
    }

    @Override
    public Object[] toArray() {
        final var result = new Object[this.count];
        System.arraycopy(this.elements, 0, result, 0, this.count);
        return result;
    }

    @Override
    public <T1> T1[] toArray(final T1[] a) {
        final var result = (T1[]) Array.newInstance(a.getClass().getComponentType(), this.count);

        //noinspection SuspiciousSystemArraycopy
        System.arraycopy(this.elements, 0, result, 0, this.count);
        return result;
    }

    @Override
    public boolean add(final T t) {
        if (this.count == this.elements.length) {
            final var newLength = this.elements.length > 0 ? this.elements.length * 2 : 1;
            final var newElements = (T[]) Array.newInstance(this.elementClass, newLength);
            System.arraycopy(this.elements, 0, newElements, 0, this.elements.length);
            this.elements = newElements;
        }

        this.elements[this.count] = t;
        this.count++;
        return true;
    }

    @Override
    public boolean remove(final Object o) {
        throw new UnsupportedOperationException("Removing elements is not supported");
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        for (final var e : c) {
            if (!this.contains(e)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(final Collection<? extends T> c) {
        if (c == this) {
            throw new IllegalStateException("Other collection cannot be this!");
        }
        for (final var e : c) {
            this.add(e);
        }
        return true;
    }

    @Override
    public boolean addAll(final int index, final Collection<? extends T> c) {
        throw new UnsupportedOperationException("Inserting elements is not supported");
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException("Removing elements is not supported");
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        if (isEmpty()) {
            return false;
        }

        final int originalSize = this.size();

        final var newElements = (T[]) Array.newInstance(this.elementClass, this.count);
        var nNewElements = 0;
        for (final var e : this) {
            if (c.contains(e)) {

                newElements[nNewElements] = e;
                ++nNewElements;
            }
        }

        this.clear();
        //noinspection ManualArrayToCollectionCopy
        for (int i = 0; i < nNewElements; ++i) {
            this.add(newElements[i]);
        }

        return this.size() != originalSize;
    }

    @Override
    public void clear() {
        this.count = 0;
    }

    @Override
    public T get(final int index) {
        if (index < 0 || index > this.count) {
            throw new IndexOutOfBoundsException(String.format("Index %d is out of bounds!", index));
        }
        return this.elements[index];
    }

    @Override
    public T set(final int index, final T element) {
        final var old = this.elements[index];
        this.elements[index] = element;
        return old;
    }

    @Override
    public void add(final int index, final T element) {
        throw new UnsupportedOperationException("Inserting elements is not supported");
    }

    @Override
    public T remove(final int index) {
        throw new UnsupportedOperationException("Removing elements is not supported");
    }

    @Override
    public int indexOf(final Object o) {
        for (int i = 0; i < this.count; ++i) {
            if (this.elements[i].equals(o)) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public int lastIndexOf(final Object o) {
        for (int i = this.count - 1; i >= 0; --i) {
            if (this.elements[i].equals(o)) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public ListIterator<T> listIterator() {
        throw new UnsupportedOperationException("ListIterator is not supported!");
    }

    @Override
    public ListIterator<T> listIterator(final int index) {
        throw new UnsupportedOperationException("ListIterator is not supported!");
    }

    @Override
    public List<T> subList(final int fromIndex, final int toIndex) {
        throw new UnsupportedOperationException("sub-lists are not supported!");
    }
}
