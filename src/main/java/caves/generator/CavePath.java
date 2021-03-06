package caves.generator;

import caves.generator.spatial.SpatialPathIndex;
import caves.util.collections.IntList;
import caves.util.math.Vector3;

public final class CavePath {
    private final Vector3[] nodes;
    private final SpatialPathIndex spatialPathIndex;

    private int nodeCount;

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
     * Gets extremum distance from nodes to the origin.
     *
     * @return the most extreme distance from any node to the origin
     */
    public float getExtremumDistance() {
        var max = Float.MIN_VALUE;
        for (final var node : this.nodes) {
            final var distanceSq = node.lengthSq();
            max = max > distanceSq ? max : distanceSq;
        }
        return (float) Math.sqrt(max);
    }

    /**
     * Gets the number of nodes on this path.
     *
     * @return the node count
     */
    public int getNodeCount() {
        return this.nodeCount;
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
     * @param position     position around which to search
     * @param radius       radius to search
     * @param foundItems   list to hold indices to the found nodes
     * @param tmpNodeQueue temporary array to hold the node queue used for searching the nodes
     */
    public void getNodesWithin(
            final Vector3 position,
            final double radius,
            final IntList foundItems,
            final SpatialPathIndex.OctreeNode[] tmpNodeQueue
    ) {
        this.spatialPathIndex.getIndicesWithin(position, radius, foundItems, tmpNodeQueue);
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
    @SuppressWarnings("unused")
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
