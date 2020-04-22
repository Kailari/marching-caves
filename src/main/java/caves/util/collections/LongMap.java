package caves.util.collections;

import javax.annotation.Nullable;
import java.util.Collection;

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
    public void put(final long index, @Nullable final T value) {
        final var entry = new Entry(index, value, null);
        final var bucket = getHash(index);

        var existing = this.buckets[bucket];
        if (existing == null) {
            if (value != null) {
                this.buckets[bucket] = entry;
                ++this.size;
            }
        } else {
            while (existing.next != null) {
                if (existing.index == index) {
                    existing.value = value;
                    if (value == null) {
                        --this.size;
                    }
                    return;
                }
                existing = existing.next;
            }

            if (existing.index == index) {
                existing.value = value;
                if (value == null) {
                    --this.size;
                }
            } else {
                if (value != null) {
                    existing.next = entry;
                    ++this.size;
                }
            }
        }
    }

    private int getHash(final long index) {
        return Math.abs(Long.hashCode(index) % this.buckets.length);
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
     * Gets all values stored in this map.
     *
     * @return all values
     */
    @SuppressWarnings("unchecked")
    public Collection<T> values() {
        final var allValues = new GrowingAddOnlyList<T>(this.size);
        for (final var bucket : this.buckets) {
            var entry = bucket;
            while (entry != null) {
                if (entry.value != null) {
                    allValues.add((T) entry.value);
                }
                entry = entry.next;
            }
        }

        return allValues;
    }

    /**
     * Clears this map. All items are destroyed and size is set to zero.
     */
    public void clear() {
        this.buckets = new Entry[this.buckets.length];
        this.size = 0;
    }

    /**
     * Gets all keys in this map.
     *
     * @return all keys
     */
    public Iterable<Long> keys() {
        final var allKeys = new GrowingAddOnlyList<Long>(this.size);
        for (final var bucket : this.buckets) {
            var entry = bucket;
            while (entry != null) {
                if (entry.value != null) {
                    allKeys.add(entry.index);
                }
                entry = entry.next;
            }
        }

        return allKeys;
    }

    private static class Entry<T> {
        private final long index;
        @Nullable private T value;

        @Nullable private Entry<T> next;

        Entry(final long index, @Nullable final T value, @Nullable final Entry<T> next) {
            this.index = index;
            this.value = value;
            this.next = next;
        }
    }
}
