package caves.generator.util;

public final class Vector3 {
    private final float x;
    private final float y;
    private final float z;

    /**
     * Gets the value of the x-component of this vector.
     *
     * @return the value of the x-component
     */
    public float getX() {
        return x;
    }

    /**
     * Gets the value of the y-component of this vector.
     *
     * @return the value of the y-component
     */
    public float getY() {
        return y;
    }

    /**
     * Gets the value of the z-component of this vector.
     *
     * @return the value of the z-component
     */
    public float getZ() {
        return z;
    }

    /**
     * Constructs a new vector from the given components.
     *
     * @param x the x-component of the vector
     * @param y the y-component of the vector
     * @param z the z-component of the vector
     */
    public Vector3(final float x, final float y, final float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Copy-constructor. Initializes a new vector with values from the another vector.
     *
     * @param other the vector which values to copy
     */
    public Vector3(final Vector3 other) {
        this(other.x, other.y, other.z);
    }

    public Vector3 add(final float x, final float y, final float z) {
        return new Vector3(this.x + x, this.y + y, this.z + z);
    }
}
