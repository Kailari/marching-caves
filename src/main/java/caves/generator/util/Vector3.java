package caves.generator.util;

public final class Vector3 {
    private float x;
    private float y;
    private float z;

    /**
     * Gets the value of the x-component of this vector.
     *
     * @return the value of the x-component
     */
    public float getX() {
        return this.x;
    }

    /**
     * Gets the value of the y-component of this vector.
     *
     * @return the value of the y-component
     */
    public float getY() {
        return this.y;
    }

    /**
     * Gets the value of the z-component of this vector.
     *
     * @return the value of the z-component
     */
    public float getZ() {
        return this.z;
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
     * Initializes a new vector with all components zeroed.
     */
    public Vector3() {
        this(0, 0, 0);
    }

    /**
     * Copy-constructor. Initializes a new vector with values from the another vector.
     *
     * @param other the vector which values to copy
     */
    public Vector3(final Vector3 other) {
        this(other.x, other.y, other.z);
    }

    public Vector3 set(final float x, final float y, final float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vector3 add(final float x, final float y, final float z, final Vector3 result) {
        return result.set(this.x + x, this.y + y, this.z + z);
    }

    public Vector3 sub(final Vector3 other, final Vector3 result) {
        return result.set(this.x - other.x, this.y - other.y, this.z - other.z);
    }
}
