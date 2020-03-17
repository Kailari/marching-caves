package caves.generator;

import caves.generator.util.Vector3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CavePath {
    private final List<Vector3> nodes;

    /**
     * Gets all nodes on the path, ordered from path start to the end.
     *
     * @return all nodes on the path
     */
    public Vector3[] getNodesOrdered() {
        return this.nodes.toArray(Vector3[]::new);
    }

    public void forEach(final Consumer<Vector3> action) {
        this.nodes.forEach(action);
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
}
