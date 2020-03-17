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

    /**
     * Sets the component values of this vector.
     *
     * @param x new value for the x-component
     * @param y new value for the y-component
     * @param z new value for the z-component
     *
     * @return self
     */
    public Vector3 set(final float x, final float y, final float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    /**
     * Calculates the sum of this vector and vector with the given components x, y and z.
     *
     * @param x      the x-component to be added
     * @param y      the y-component to be added
     * @param z      the z-component to be added
     * @param result vector for storing the result
     *
     * @return vector storing the result
     */
    public Vector3 add(final float x, final float y, final float z, final Vector3 result) {
        return result.set(this.x + x, this.y + y, this.z + z);
    }

    /**
     * Calculates the sum of this vector and the given other vector.
     *
     * @param other  the other vector to be summed
     * @param result vector for storing the result
     *
     * @return vector storing the result
     */
    public Vector3 add(final Vector3 other, final Vector3 result) {
        return add(other.x, other.y, other.z, result);
    }

    public Vector3 sub(final Vector3 other, final Vector3 result) {
        return result.set(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    public Vector3 min(final Vector3 other, final Vector3 result) {
        return result.set(Math.min(this.x, other.x),
                          Math.min(this.y, other.y),
                          Math.min(this.z, other.z));
    }

    public Vector3 max(final Vector3 other, final Vector3 result) {
        return result.set(Math.max(this.x, other.x),
                          Math.max(this.y, other.y),
                          Math.max(this.z, other.z));
    }

    public float distanceSq(final Vector3 pos) {
        final var dx = this.x - pos.x;
        final var dy = this.y - pos.y;
        final var dz = this.z - pos.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Calculates the cross product of this vector and the given other vector and stores it in the
     * result vector. It is safe to pass this as the result vector.
     *
     * @param other  the other vector
     * @param result the result
     *
     * @return the result vector
     */
    public Vector3 cross(final Vector3 other, final Vector3 result) {
        return result.set(Math.fma(this.y, other.z, -this.z * other.y),
                          Math.fma(this.z, other.x, -this.x * other.z),
                          Math.fma(this.x, other.y, -this.y * other.x));
    }

    /**
     * Calculates the squared length of this vector.
     *
     * @return the squared length
     */
    public float lengthSq() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    public float length() {
        return (float) Math.sqrt(lengthSq());
    }

    public Vector3 normalize() {
        final var length = this.length();
        this.x /= length;
        this.y /= length;
        this.z /= length;
        return this;
    }

    public float dot(final Vector3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    @Override
    public String toString() {
        return "Vector3{"
                + "x=" + x
                + ", y=" + y
                + ", z=" + z
                + '}';
    }
}
