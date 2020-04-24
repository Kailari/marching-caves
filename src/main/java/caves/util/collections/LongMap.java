package caves.util.collections;

import javax.annotation.Nullable;
import java.util.ConcurrentModificationException;

/**
 * Map with longs as keys.
 *
 * @param <T> Type of the stored elements.
 */
public class LongMap<T> {
    @SuppressWarnings("rawtypes")
    private Entry[] buckets;

    private int size;

    /**
     * Gets the number of elements in this map .
     *
     * @return the number of values stored
     */
    public int getSize() {
        return this.size;
    }

    /**
     * Creates a new map with initial capacity.
     *
     * @param initialCapacity initial capacity
     */
    public LongMap(final int initialCapacity) {
        this.buckets = new Entry[initialCapacity];
    }

    /**
     * Adds a new value to the map.
     *
     * @param index key of the added value
     * @param value added value
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void put(final long index, final T value) {
        final var entry = new Entry(index, value, null);
        final var bucket = getHash(index);

        var existing = this.buckets[bucket];
        if (existing == null) {
            this.buckets[bucket] = entry;
            ++this.size;
        } else {
            while (existing.next != null) {
                if (existing.index == index) {
                    existing.value = value;
                    return;
                }
                existing = existing.next;
            }

            if (existing.index == index) {
                existing.value = value;
            } else {
                existing.next = entry;
                ++this.size;
            }
        }
    }

    private int getHash(final long index) {
        final var hash = (index ^ (index >>> 30) ^ (index << 30)) % this.buckets.length;
        return (int) (hash < 0 ? -hash : hash);
    }

    /**
     * Gets a chunk with the given index from the map.
     *
     * @param index the index of the chunk
     *
     * @return the chunk with the given index or <code>null</code> if none are found
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public T get(final long index) {
        var entry = this.buckets[getHash(index)];
        while (entry != null) {
            if (entry.index == index) {
                return (T) entry.value;
            }
            entry = entry.next;
        }

        return null;
    }

    /**
     * Gets an iterator over all values stored in this map. The iterator should be treated as
     * invalidated if the map is modified.
     *
     * @return all values
     */
    public Iterable<T> values() {
        return BucketIterator::new;
    }

    /**
     * Clears this map. All items are destroyed and size is set to zero.
     */
    public void clear() {
        this.buckets = new Entry[this.buckets.length];
        this.size = 0;
    }

    private static class Entry<T> {
        private final long index;
        private T value;

        @Nullable private Entry<T> next;

        Entry(final long index, final T value, @Nullable final Entry<T> next) {
            this.index = index;
            this.value = value;
            this.next = next;
        }
    }

    @SuppressWarnings("rawtypes")
    private class BucketIterator implements java.util.Iterator<T> {
        private final int initialSize;
        private int currentBucket;
        @Nullable private Entry current;
        private int traversed;

        public BucketIterator() {
            this.currentBucket = -1;
            this.initialSize = getSize();
        }

        @Override
        public boolean hasNext() {
            return this.traversed < getSize();
        }

        @Override
        @SuppressWarnings("unchecked")
        public T next() {
            if (this.initialSize != getSize()) {
                throw new ConcurrentModificationException("The backing LongMap was modified while iteration was in progress!");
            }

            if (this.current != null && this.current.next != null) {
                this.current = this.current.next;
                ++this.traversed;
                return (T) this.current.value;
            }

            while (this.currentBucket + 1 < LongMap.this.buckets.length) {
                ++this.currentBucket;
                this.current = LongMap.this.buckets[this.currentBucket];

                if (this.current != null) {
                    ++this.traversed;
                    return (T) this.current.value;
                }
            }

            throw new IllegalStateException("Ran out of buckets! Traversed: " + this.traversed
                                                    + ", size: " + getSize());
        }
    }
}
