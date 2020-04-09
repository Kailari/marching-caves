package caves.generator;

import caves.generator.spatial.SpatialPathIndex;
import caves.util.collections.GrowingAddOnlyList;
import caves.util.math.Vector3;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class CavePath {
    private final List<Vector3> nodes;
    private final SpatialPathIndex spatialPathIndex;
    private final double nodeSpacing;

    /**
     * How many branches can start from a single node? 1 means no branching.
     *
     * @return branching limit per node
     */
    public int getSplittingLimit() {
        return 1;
    }

    /**
     * Gets the distance between nodes on the path.
     *
     * @return distance between nodes
     */
    public double getNodeSpacing() {
        return this.nodeSpacing;
    }

    /**
     * Gets all nodes on the path, ordered from path start to the end.
     *
     * @return all nodes on the path
     */
    public Vector3[] getNodesOrdered() {
        return this.nodes.toArray(Vector3[]::new);
    }

    /**
     * Gets the average position of all path nodes.
     *
     * @return the average position
     */
    public Vector3 getAveragePosition() {
        final Vector3 sum = new Vector3(0.0f, 0.0f, 0.0f);
        if (this.nodes.isEmpty()) {
            return sum;
        }

        for (final var node : this.nodes) {
            sum.add(node);
        }

        return sum.set(sum.getX() / this.nodes.size(),
                       sum.getY() / this.nodes.size(),
                       sum.getZ() / this.nodes.size());
    }

    /**
     * Gets all nodes on the path. The order is not guaranteed.
     *
     * @return all nodes on the path
     */
    public Collection<Vector3> getAllNodes() {
        return this.nodes;
    }

    /**
     * Constructs a new empty path.
     *
     * @param nodeSpacing        distance between nodes
     * @param maxInfluenceRadius maximum density influence radius of a node
     */
    public CavePath(final double nodeSpacing, final float maxInfluenceRadius) {
        this.nodeSpacing = nodeSpacing;
        this.nodes = new GrowingAddOnlyList<>(Vector3.class, 32);
        this.spatialPathIndex = new SpatialPathIndex((float) (maxInfluenceRadius + nodeSpacing));
    }

    /**
     * Adds a node to the path. The node is added to the end of the path.
     *
     * @param node the node to append
     */
    public void addNode(final Vector3 node) {
        final var index = this.nodes.size();
        this.nodes.add(node);
        this.spatialPathIndex.insert(node, index);
    }

    /**
     * Gets all nodes within the radius. Implementation must return all nodes that are within the
     * radius. Returning nodes outside the radius is allowed, but results in additional
     * computational cost elsewhere.
     *
     * @param position position around which to search
     * @param radius   radius to search
     *
     * @return indices of all nodes within the radius
     */
    public Collection<Integer> getNodesWithin(final Vector3 position, final double radius) {
        if (true) {
            return this.spatialPathIndex.getIndicesWithin(this::get, position, radius);
        }

        final var radiusSq = radius * radius;

        final var nodes = new GrowingAddOnlyList<>(Integer.class, this.nodes.size());
        for (int i = 0; i < this.nodes.size(); i++) {
            if (this.nodes.get(i).distanceSq(position) < radiusSq) {
                nodes.add(i);
            }
        }
        return nodes;
    }

    /**
     * Gets the index of the given node's parent node.
     *
     * @param index index of the node
     *
     * @return the index of the previous (parent) node
     */
    public Optional<Integer> getPreviousFor(final int index) {
        return index > 0
                ? Optional.of(index - 1)
                : Optional.empty();
    }

    /**
     * Gets the index of the given node's parent node.
     *
     * @param index index of the node
     *
     * @return the index of the previous (parent) node
     */
    public Collection<Integer> getNextFor(final int index) {
        return index == this.nodes.size() - 1
                ? List.of()
                : List.of(index + 1);
    }

    /**
     * Gets the node associated with the given index.
     *
     * @param index the index of the node
     *
     * @return the node
     */
    public Vector3 get(final int index) {
        return this.nodes.get(index);
    }
}
