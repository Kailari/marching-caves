package caves.util.math;

public class IntVector3 {
    private int x;
    private int y;
    private int z;

    /**
     * Gets the value of the x-component of this vector.
     *
     * @return the value of the x-component
     */
    public int getX() {
        return this.x;
    }

    /**
     * Gets the value of the y-component of this vector.
     *
     * @return the value of the y-component
     */
    public int getY() {
        return this.y;
    }

    /**
     * Gets the value of the z-component of this vector.
     *
     * @return the value of the z-component
     */
    public int getZ() {
        return this.z;
    }

    /**
     * Constructs a new vector with all components initialized as zeroes.
     */
    public IntVector3() {
        this(0, 0, 0);
    }

    /**
     * Constructs a new vector from the given components.
     *
     * @param x the x-component of the vector
     * @param y the y-component of the vector
     * @param z the z-component of the vector
     */
    public IntVector3(final int x, final int y, final int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Sets the component values of this vector.
     *
     * @param x new value for the x-component
     * @param y new value for the y-component
     * @param z new value for the z-component
     *
     * @return self
     */
    public IntVector3 set(final int x, final int y, final int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }
}
