package caves.util.math;

public class BoundingBox {
    private final Vector3 min;
    private final Vector3 max;

    /**
     * Gets the minimum (closest to negative infinity) coordinates of this box.
     *
     * @return the minimum corner point
     */
    public Vector3 getMin() {
        return this.min;
    }

    /**
     * Gets the maximum (closest to positive infinity) coordinates of this box.
     *
     * @return the maximum corner point
     */
    public Vector3 getMax() {
        return this.max;
    }

    /**
     * Gets the center of this AABB. Calculated as the average of the min/max bounds.
     *
     * @return the center point
     */
    public Vector3 getCenter() {
        return this.min.add(this.max, new Vector3())
                       .mul(1.0f / 2.0f);
    }

    /**
     * Gets the size of this bounding box on the x-axis.
     *
     * @return the size on the x axis
     */
    public float getSizeX() {
        return Math.abs(this.max.getX() - this.min.getX());
    }

    /**
     * Gets the size of this bounding box on the y-axis.
     *
     * @return the size on the y axis
     */
    public float getSizeY() {
        return Math.abs(this.max.getY() - this.min.getY());
    }

    /**
     * Gets the size of this bounding box on the z-axis.
     *
     * @return the size on the z axis
     */
    public float getSizeZ() {
        return Math.abs(this.max.getZ() - this.min.getZ());
    }

    /**
     * Initializes a new bounding box with given minimum and maximum coordinates.
     *
     * @param min minimum coordinates
     * @param max maximum coordinates
     */
    public BoundingBox(final Vector3 min, final Vector3 max) {
        this.min = min;
        this.max = max;
    }

    /**
     * Checks whether or not the position is contained within this node. Points on the edges are
     * treated as contained.
     *
     * @param position position to check
     *
     * @return <code>true</code> if the position is inside or on the edge of this AABB
     */
    public boolean contains(final Vector3 position) {
        return position.getX() >= this.min.getX() && position.getX() <= this.max.getX()
                && position.getY() >= this.min.getY() && position.getY() <= this.max.getY()
                && position.getZ() >= this.min.getZ() && position.getZ() <= this.max.getZ();
    }
}
