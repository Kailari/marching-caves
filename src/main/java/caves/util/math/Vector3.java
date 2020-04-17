package caves.util.math;

import java.util.Objects;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public final class Vector3 {
    public float x;
    public float y;
    public float z;

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
     * Copy-constructor. Initializes a new vector with values from the another vector.
     *
     * @param other the vector which values to copy
     */
    public Vector3(final IntVector3 other) {
        this(other.getX(), other.getY(), other.getZ());
    }

    /**
     * Performs linear interpolation between the points A and B.
     *
     * @param a      point A
     * @param b      point B
     * @param alpha  alpha value
     * @param result vector to hold the result
     *
     * @return vector holding the result
     */
    public static Vector3 lerp(
            final Vector3 a,
            final Vector3 b,
            final float alpha,
            final Vector3 result
    ) {
        return result.set(Math.fma(b.x - a.x, alpha, a.x),
                          Math.fma(b.y - a.y, alpha, a.y),
                          Math.fma(b.z - a.z, alpha, a.z));
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
     * Calculates the sum of this vector and vector with the given components x, y and z. Note that
     * this modifies the original.
     *
     * @param x the x-component to be added
     * @param y the y-component to be added
     * @param z the z-component to be added
     *
     * @return this vector, with the given components added
     */
    public Vector3 add(final float x, final float y, final float z) {
        return add(x, y, z, this);
    }

    /**
     * Calculates the sum of this vector and the given other vector.
     *
     * @param other  the other vector to be summed
     * @param result vector to hold the result
     *
     * @return vector holding the result
     */
    public Vector3 add(final Vector3 other, final Vector3 result) {
        return add(other.x, other.y, other.z, result);
    }

    /**
     * Calculates the sum of this vector and the given other vector. Note that this modifies the
     * original.
     *
     * @param other the other vector to be summed
     *
     * @return this vector, with the other vector added
     */
    public Vector3 add(final Vector3 other) {
        return add(other, this);
    }

    /**
     * Calculates the subtraction of this vector and the given other vector.
     *
     * @param other  the other vector to subtract from this
     * @param result vector to hold the result
     *
     * @return vector holding the result
     */
    public Vector3 sub(final Vector3 other, final Vector3 result) {
        result.x = this.x - other.x;
        result.y = this.y - other.y;
        result.z = this.z - other.z;
        return result;
    }

    /**
     * Calculates the subtraction of this vector and vector with the given components x, y and z.
     *
     * @param x      the x-component to be subtracted
     * @param y      the y-component to be subtracted
     * @param z      the z-component to be subtracted
     * @param result vector to hold the result
     *
     * @return vector holding the result
     */
    public Vector3 sub(final float x, final float y, final float z, final Vector3 result) {
        result.x = this.x - x;
        result.y = this.y - y;
        result.z = this.z - z;
        return result;
    }

    /**
     * Calculates the subtraction of this vector and the given other vector. Note that this modifies
     * the original.
     *
     * @param other the other vector to subtract from this
     *
     * @return this vector, with the other vector subtracted
     */
    public Vector3 sub(final Vector3 other) {
        this.x -= other.x;
        this.y -= other.y;
        this.z -= other.z;
        return this;
    }

    /**
     * Calculates the subtraction of this vector and the given components of another vector. Note
     * that this modifies the original.
     *
     * @param x the x-component to be subtracted
     * @param y the y-component to be subtracted
     * @param z the z-component to be subtracted
     *
     * @return this vector, with the components subtracted
     */
    public Vector3 sub(final float x, final float y, final float z) {
        this.x -= x;
        this.y -= y;
        this.z -= z;
        return this;
    }

    /**
     * Calculates the scalar multiplication of this vector with a scalar.
     *
     * @param scalar the multiplier
     * @param result vector to hold the result
     *
     * @return vector holding the result
     */
    public Vector3 mul(final float scalar, final Vector3 result) {
        result.x = this.x * scalar;
        result.y = this.y * scalar;
        result.z = this.z * scalar;
        return result;
    }

    /**
     * Calculates the scalar multiplication of this vector with a scalar. Note that this modifies
     * the original.
     *
     * @param scalar the multiplier
     *
     * @return this vector, multiplied by the scalar
     */
    public Vector3 mul(final float scalar) {
        this.x *= scalar;
        this.y *= scalar;
        this.z *= scalar;
        return this;
    }

    /**
     * Calculates the component-wise minimum of this and the given other vector.
     *
     * @param other  the other vector
     * @param result vector to hold the result
     *
     * @return vector holding the result
     */
    public Vector3 min(final Vector3 other, final Vector3 result) {
        return result.set(Math.min(this.x, other.x),
                          Math.min(this.y, other.y),
                          Math.min(this.z, other.z));
    }

    /**
     * Calculates the component-wise maximum of this and the given other vector.
     *
     * @param other  the other vector
     * @param result vector to hold the result
     *
     * @return vector holding the result
     */
    public Vector3 max(final Vector3 other, final Vector3 result) {
        return result.set(Math.max(this.x, other.x),
                          Math.max(this.y, other.y),
                          Math.max(this.z, other.z));
    }

    /**
     * Calculates dot-product between this vector and the given other vector.
     *
     * @param other the other vector
     *
     * @return the dot product
     */
    public float dot(final Vector3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
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
     * Calculates the squared distance between this and the given other vector.
     *
     * @param other the other vector
     *
     * @return the distance squared
     */
    public float distanceSq(final Vector3 other) {
        final var dx = this.x - other.x;
        final var dy = this.y - other.y;
        final var dz = this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Calculates the squared distance between this and the given other vector.
     *
     * @param x the x coordinate of the other vector
     * @param y the y coordinate of the other vector
     * @param z the z coordinate of the other vector
     *
     * @return the distance squared
     */
    public float distanceSq(final float x, final float y, final float z) {
        final var dx = this.x - x;
        final var dy = this.y - y;
        final var dz = this.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Calculates the distance between this and the given other vector.
     *
     * @param other the other vector
     *
     * @return the distance
     */
    public float distance(final Vector3 other) {
        return (float) Math.sqrt(this.distanceSq(other));
    }

    /**
     * Calculates the squared length of this vector.
     *
     * @return the squared length
     */
    public float lengthSq() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    /**
     * Calculates the length of this vector.
     *
     * @return the length
     */
    public float length() {
        return (float) Math.sqrt(lengthSq());
    }

    /**
     * Normalizes this vector. Does not modify self
     *
     * @param result vector to hold the result
     *
     * @return copy of this vector, normalized.
     */
    public Vector3 normalize(final Vector3 result) {
        final var length = this.length();
        return result.set(this.x / length,
                          this.y / length,
                          this.z / length);
    }

    /**
     * Normalizes this vector. Note that this modifies the original.
     *
     * @return this vector, normalized.
     */
    public Vector3 normalize() {
        final var length = this.length();
        if (length < Float.MIN_VALUE) {
            throw new ArithmeticException("Normalizing a zero-length vector results in division by zero!");
        }

        this.x /= length;
        this.y /= length;
        this.z /= length;
        return this;
    }

    /**
     * Takes component-wise absolute of this vector. Note that this modifies the original.
     *
     * @return component wise absolute of this vector
     */
    public Vector3 abs() {
        return this.set(Math.abs(this.x), Math.abs(this.y), Math.abs(this.z));
    }

    @Override
    public String toString() {
        return "Vector3{"
                + "x=" + this.x
                + ", y=" + this.y
                + ", z=" + this.z
                + '}';
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Vector3)) {
            return false;
        }
        final var otherVector = (Vector3) other;
        return Float.compare(otherVector.getX(), getX()) == 0
                && Float.compare(otherVector.getY(), getY()) == 0
                && Float.compare(otherVector.getZ(), getZ()) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getX(), getY(), getZ());
    }
}
