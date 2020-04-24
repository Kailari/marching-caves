package caves.util.collections;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Collection which supports only adding elements.
 *
 * @param <T> element type
 */
@SuppressWarnings("unchecked")
public final class SimpleList<T> implements Iterable<T> {
    private int count;
    private Object[] elements;

    /**
     * Creates a new collection with the given element type.
     */
    public SimpleList() {
        this(32);
    }

    /**
     * Creates a new collection with the given element type and initial capacity.
     *
     * @param capacity initial capacity to allocate for the collection
     */
    public SimpleList(final int capacity) {
        this.elements = new Object[capacity];
    }

    public int size() {
        return this.count;
    }

    public Iterator<T> iterator() {
        return new Iterator<>() {
            private int index = -1;

            @Override
            public boolean hasNext() {
                return this.index + 1 < SimpleList.this.count;
            }

            @Override
            public T next() {
                this.index++;
                if (this.index >= SimpleList.this.count) {
                    throw new NoSuchElementException();
                }

                return (T) SimpleList.this.elements[this.index];
            }
        };
    }

    public boolean add(final T t) {
        if (this.count == this.elements.length) {
            final var newLength = this.elements.length > 0 ? this.elements.length * 2 : 1;
            final var newElements = new Object[newLength];
            System.arraycopy(this.elements, 0, newElements, 0, this.elements.length);
            this.elements = newElements;
        }

        this.elements[this.count] = t;
        this.count++;
        return true;
    }

    public void clear() {
        this.count = 0;
    }

    public T get(final int index) {
        if (index < 0 || index > this.count) {
            throw new IndexOutOfBoundsException(String.format("Index %d is out of bounds!", index));
        }
        return (T) this.elements[index];
    }
}
