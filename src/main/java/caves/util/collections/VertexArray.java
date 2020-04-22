package caves.util.collections;

import java.util.function.Function;

public class VertexArray<T> {
    private Object[] elements;
    private int count;

    public VertexArray(final int initialCapacity) {
        this.elements = new Object[initialCapacity];
    }

    public int size() {
        return this.count;
    }

    public synchronized int append(final int n) {
        if (this.count + n >= this.elements.length) {
            expand();
        }

        final var offset = this.count;
        this.count += n;
        return offset;
    }

    public void set(final int index, final T item) {
        this.elements[index] = item;
    }

    @SuppressWarnings("unchecked")
    public synchronized <R> void mappingCopyTo(final R[] result, final Function<T, R> mapper) {
        for (int i = 0; i < result.length; ++i) {
            result[i] = mapper.apply((T) this.elements[i]);
        }
    }

    private synchronized void expand() {
        final var minGrowth = 1;
        final var maxGrowth = this.elements.length >> 1;

        //noinspection ManualMinMaxCalculation
        final var growth = maxGrowth > minGrowth ? maxGrowth : minGrowth;
        final var newSize = this.elements.length + growth;
        final var newElements = new Object[newSize];
        System.arraycopy(this.elements, 0, newElements, 0, this.elements.length);

        this.elements = newElements;
    }
}
