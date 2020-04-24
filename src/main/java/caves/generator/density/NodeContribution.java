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
     * Gets the floorness multiplier.
     *
     * @return the floor multiplier
     */
    public double getFloorness() {
        return this.floorness;
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
     * Gets the distance to nearest point on path.
     *
     * @return the minimum distance
     */
    public double getDistance() {
        return this.distance;
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

    /**
     * Resets this contribution to the given values.
     *
     * @param floorWeight      new floorness
     * @param caveContribution new contribution value
     * @param distance         new distance
     * @param distanceAlpha    new distance alpha
     * @param hasContribution  new contribution status
     */
    public void set(
            final double floorWeight,
            final double caveContribution,
            final double distance,
            final double distanceAlpha,
            final boolean hasContribution
    ) {
        this.floorness = floorWeight;
        this.value = caveContribution;
        this.distance = distance;
        this.weight = distanceAlpha;
        this.hasContribution = hasContribution;
    }

    /**
     * Combines values from this and the other contribution. Performs a series of min/max
     * operations.
     *
     * @param other    the other contribution to combine with this one
     * @param weightSq the squared weight to use as the other weight
     */
    public void combine(final NodeContribution other, final double weightSq) {
        this.weight = weightSq > this.weight ? weightSq : this.weight;
        this.value = other.value < this.value ? other.value : this.value;
        this.distance = other.distance < this.distance ? other.distance : this.distance;
        this.floorness = other.floorness < this.floorness ? other.floorness : this.floorness;
        this.hasContribution = true;
    }
}
