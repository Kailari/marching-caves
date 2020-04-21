package caves.util.collections;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * Map with longs as keys.
 */
public class LongMap<T> {
    @SuppressWarnings("rawtypes")
    private Entry[] buckets;

    private int size;

    /**
     * Gets the number of elements in this map .
     *
     * @return the number of chunks stored
     */
    public int getSize() {
        return this.size;
    }

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
        final var bucket = index % this.buckets.length;

        var existing = this.buckets[(int) bucket];
        if (existing == null) {
            if (value != null) {
                this.buckets[(int) bucket] = entry;
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
        var entry = this.buckets[(int) (index % this.buckets.length)];
        while (entry != null) {
            if (entry.index == index) {
                return (T) entry.value;
            }
            entry = entry.next;
        }

        return null;
    }

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

    public T createIfAbsent(
            final long index,
            final Supplier<T> valueSupplier
    ) {
        final var chunk = get(index);
        if (chunk != null) {
            return chunk;
        }

        final var newValue = valueSupplier.get();
        put(index, newValue);
        return newValue;
    }

    public void clear() {
        this.buckets = new Entry[this.buckets.length];
        this.size = 0;
    }

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

        public Entry(final long index, @Nullable final T value, @Nullable final Entry<T> next) {
            this.index = index;
            this.value = value;
            this.next = next;
        }
    }
}
