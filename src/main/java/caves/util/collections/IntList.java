package caves.util.collections;

public class IntList {
    private int[] elements = new int[0];
    private int count;

    /**
     * Gets the number of elements in this list.
     *
     * @return the element count
     */
    public int getCount() {
        return this.count;
    }

    /**
     * Gets the backing array for this list. Use this with {@link #getCount()} for fast queries.
     *
     * @return the backing array
     */
    public int[] getBackingArray() {
        return this.elements;
    }

    /**
     * Adds all items from the given array to this list.
     *
     * @param items items to add
     */
    public void addAll(final int[] items) {
        if (this.count + items.length >= this.elements.length) {
            final var maxGrowth = this.count >> 1;
            final var growth = maxGrowth < items.length ? items.length : maxGrowth;

            final var newElements = new int[this.count + growth];
            System.arraycopy(this.elements, 0, newElements, 0, this.elements.length);
            this.elements = newElements;
        }

        System.arraycopy(items, 0, this.elements, this.count, items.length);
        this.count += items.length;
    }

    /**
     * Clears this list. Sets the count to zero.
     */
    public void clear() {
        this.count = 0;
    }
}
