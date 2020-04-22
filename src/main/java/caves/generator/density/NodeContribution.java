package caves.generator.density;

public final class NodeContribution {
    private boolean hasContribution;
    private double value;
    private double floorness;
    private double weight;
    private double distance;

    /**
     * Gets the contribution value.
     *
     * @return the contribution value
     */
    public double getValue() {
        return this.value;
    }

    /**
     * Sets the contribution value.
     *
     * @param value new value
     */
    public void setValue(final double value) {
        this.value = value;
    }

    /**
     * Gets the floorness multiplier.
     *
     * @return the floor multiplier
     */
    public double getFloorness() {
        return this.floorness;
    }

    /**
     * Sets the floorness multiplier.
     *
     * @param floorness new value
     */
    public void setFloorness(final double floorness) {
        this.floorness = floorness;
    }

    /**
     * Gets the weight.
     *
     * @return the weight
     */
    public double getWeight() {
        return this.weight;
    }

    /**
     * Sets the weight.
     *
     * @param weight the weight
     */
    public void setWeight(final double weight) {
        this.weight = weight;
    }

    /**
     * Gets the distance to nearest point on path.
     *
     * @return the minimum distance
     */
    public double getDistance() {
        return this.distance;
    }

    /**
     * Sets the distance.
     *
     * @param distance new value
     */
    public void setDistance(final double distance) {
        this.distance = distance;
    }

    NodeContribution() {
        this.value = 0.0;
        this.weight = 0.0;
        this.floorness = 0.0;
        this.distance = Double.MAX_VALUE;
        this.hasContribution = false;
    }

    /**
     * Checks if there is a contribution.
     *
     * @return <code>true</code> if this instance holds a valid contribution
     */
    public boolean hasContribution() {
        return this.hasContribution;
    }

    /**
     * Sets the contribution status.
     *
     * @param hasContribution new status
     */
    public void setHasContribution(final boolean hasContribution) {
        this.hasContribution = hasContribution;
    }

    /**
     * Clears this contribution. Allows setting the contribution state.
     *
     * @param hasContribution the contribution state
     */
    public void clear(final boolean hasContribution) {
        this.value = hasContribution ? -1.0 : 0.0;
        this.floorness = 0.0;
        this.weight = hasContribution ? 1.0 : 0.0;
        this.distance = hasContribution ? 0.0 : Double.MAX_VALUE;
        this.hasContribution = hasContribution;
    }
}
