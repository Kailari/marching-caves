package caves.util.collections;

import caves.generator.SampleSpaceChunk;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class ChunkMap {
    private final Entry[] buckets;

    /** Cache/helper for easily getting list of all values, fast. */
    private final GrowingAddOnlyList<SampleSpaceChunk> values;

    private int size;

    /**
     * Gets the number of elements in this map .
     *
     * @return the number of chunks stored
     */
    public int getSize() {
        return this.size;
    }

    public ChunkMap(final int initialCapacity) {
        this.buckets = new Entry[initialCapacity];
        this.values = new GrowingAddOnlyList<>(initialCapacity);
    }

    /**
     * Adds a new value to the map. Does not safeguard against adding the same key multiple times.
     *
     * @param index key of the added value
     * @param chunk added chunk
     */
    public void put(final long index, final SampleSpaceChunk chunk) {
        final var entry = new Entry(index, chunk, null);
        final var bucket = index % this.buckets.length;

        var existing = this.buckets[(int) bucket];
        this.values.add(chunk);
        ++this.size;
        if (existing == null) {
            this.buckets[(int) bucket] = entry;
        } else {
            while (existing.next != null) {
                existing = existing.next;
            }
            existing.next = entry;
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
    public SampleSpaceChunk get(final long index) {
        var entry = this.buckets[(int) (index % this.buckets.length)];
        while (entry != null) {
            if (entry.index == index) {
                return entry.chunk;
            }
            entry = entry.next;
        }

        return null;
    }

    public Iterable<SampleSpaceChunk> values() {
        return this.values;
    }

    public SampleSpaceChunk createIfAbsent(
            final long index,
            final Supplier<SampleSpaceChunk> chunkSupplier
    ) {
        final var chunk = get(index);
        if (chunk != null) {
            return chunk;
        }

        final var newChunk = chunkSupplier.get();
        put(index, newChunk);
        return newChunk;
    }

    private static class Entry {
        private final long index;
        private final SampleSpaceChunk chunk;

        @Nullable private Entry next;

        public Entry(
                final long index,
                final SampleSpaceChunk chunk,
                @Nullable final Entry next
        ) {
            this.index = index;
            this.chunk = chunk;
            this.next = next;
        }
    }
}
