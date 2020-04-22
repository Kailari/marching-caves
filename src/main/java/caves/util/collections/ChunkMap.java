package caves.util.collections;

import caves.generator.SampleSpaceChunk;

public class ChunkMap extends LongMap<SampleSpaceChunk> {
    private final float surfaceLevel;

    /**
     * Creates a new chunk map with initial capacity.
     *
     * @param initialCapacity initial capacity
     * @param surfaceLevel    isosurface density level
     */
    public ChunkMap(final int initialCapacity, final float surfaceLevel) {
        super(initialCapacity);
        this.surfaceLevel = surfaceLevel;
    }

    /**
     * Creates the given entry using the supplier if it does not exist already.
     *
     * @param index index to check
     *
     * @return the created or stored value
     */
    public SampleSpaceChunk createIfAbsent(final long index) {
        final var chunk = get(index);
        if (chunk != null) {
            return chunk;
        }

        final var newValue = new SampleSpaceChunk(this.surfaceLevel);
        put(index, newValue);
        return newValue;
    }
}
