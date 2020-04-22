package caves.util;

import caves.util.collections.LongMap;

import java.util.function.Supplier;

public final class ThreadedResourcePool<T> {
    private final LongMap<T> entries;
    private final Supplier<T> factory;

    /**
     * Creates new resource pool with given capacity.
     *
     * @param factory resource factory
     */
    public ThreadedResourcePool(final Supplier<T> factory) {
        this.factory = factory;
        this.entries = new LongMap<>(32);
    }

    /**
     * Gets a single resource from this pool.
     *
     * @return the resource
     */
    public T get() {
        final var key = Thread.currentThread().getId();
        final var resource = this.entries.get(key);
        if (resource == null) {
            synchronized (this.entries) {
                final var newResource = this.factory.get();
                this.entries.put(key, newResource);
                return newResource;
            }
        } else {
            return resource;
        }
    }
}
