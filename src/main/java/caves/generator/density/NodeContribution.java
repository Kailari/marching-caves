package caves.generator.density;

public final class NodeContribution {
    public double value;
    public double floorness;
    public double weight;
    public double distance;

    public boolean hasContribution;

    NodeContribution() {
        this.value = 0.0;
        this.weight = 0.0;
        this.floorness = 0.0;
        this.distance = Double.MAX_VALUE;
        this.hasContribution = false;
    }
}
