package caves.generator;

import caves.util.math.Vector3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class CavePath {
    private final List<Vector3> nodes;

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
        return this.nodes.stream()
                         .reduce((a, b) -> a.add(b, new Vector3()))
                         .map(sum -> sum.mul(1.0f / this.nodes.size(), sum))
                         .orElseThrow();
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
     */
    public CavePath() {
        this.nodes = new ArrayList<>();
    }

    /**
     * Adds a node to the path. The node is added to the end of the path.
     *
     * @param node the node to append
     */
    public void addNode(final Vector3 node) {
        this.nodes.add(node);
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
        return IntStream.range(0, this.nodes.size())
                        .boxed()
                        .collect(Collectors.toList());
    }

    /**
     * Gets the index of the given node's parent node.
     *
     * @param index index of the node
     *
     * @return the index of the previous (parent) node
     */
    public Optional<Integer> getPreviousFor(final int index) {
        return Optional.of(index - 1);
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
