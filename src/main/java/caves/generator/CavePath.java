package caves.generator;

import caves.generator.spatial.SpatialPathIndex;
import caves.util.math.Vector3;

import java.util.Collection;
import java.util.List;

public final class CavePath {
    private final Vector3[] nodes;
    private final SpatialPathIndex spatialPathIndex;

    private int nodeCount;

    /**
     * How many branches can start from a single node? 1 means no branching.
     *
     * @return branching limit per node
     */
    public int getSplittingLimit() {
        return 1;
    }

    /**
     * Gets all nodes on the path, ordered from path start to the end.
     *
     * @return all nodes on the path
     */
    public Vector3[] getNodesOrdered() {
        return this.nodes;
    }

    /**
     * Gets the average position of all path nodes.
     *
     * @return the average position
     */
    public Vector3 getAveragePosition() {
        final Vector3 sum = new Vector3(0.0f, 0.0f, 0.0f);
        for (final var node : this.nodes) {
            sum.add(node);
        }

        return sum.set(sum.getX() / this.nodes.length,
                       sum.getY() / this.nodes.length,
                       sum.getZ() / this.nodes.length);
    }

    /**
     * Gets all nodes on the path. The order is not guaranteed.
     *
     * @return all nodes on the path
     */
    public Collection<Vector3> getAllNodes() {
        return List.of(this.nodes);
    }

    /**
     * Constructs a new empty path.
     *
     * @param nodeCount          node capacity
     * @param maxInfluenceRadius maximum density influence radius of a node
     */
    public CavePath(final int nodeCount, final float maxInfluenceRadius) {
        this.nodes = new Vector3[nodeCount];
        this.spatialPathIndex = new SpatialPathIndex(maxInfluenceRadius);
    }

    /**
     * Adds a node to the path. The node is added to the end of the path.
     *
     * @param node the node to append
     */
    public void addNode(final Vector3 node) {
        final var index = this.nodeCount;
        this.nodeCount++;

        this.nodes[index] = node;
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
    public int[] getNodesWithin(final Vector3 position, final double radius) {
        return this.spatialPathIndex.getIndicesWithin(position, radius);
    }

    /**
     * Gets the index of the given node's parent node.
     *
     * @param index index of the node
     *
     * @return the index of the previous (parent) node
     */
    public int getPreviousFor(final int index) {
        return index > 0 ? index - 1 : -1;
    }

    /**
     * Gets the index of the given node's parent node.
     *
     * @param index index of the node
     *
     * @return the index of the previous (parent) node
     */
    public int[] getNextFor(final int index) {
        return index == this.nodes.length - 1
                ? new int[0]
                : new int[]{index + 1};
    }

    /**
     * Gets the node associated with the given index.
     *
     * @param index the index of the node
     *
     * @return the node
     */
    public Vector3 get(final int index) {
        return this.nodes[index];
    }
}
