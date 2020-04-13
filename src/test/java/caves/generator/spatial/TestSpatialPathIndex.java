package caves.generator.spatial;

import caves.util.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TestSpatialPathIndex {
    @Test
    void rootIsAtDepthZero() {
        final var index = new SpatialPathIndex(2);
        index.insert(new Vector3(0, 0, 0), 0);
        final var root = index.getRootNode();
        assertEquals(0, root.getDepth());
    }

    @Test
    void rootHasExpectedSize() {
        final var index = new SpatialPathIndex(2);
        index.insert(new Vector3(0, 0, 0), 0);
        final var root = index.getRootNode();
        assertEquals(-2, root.getMin().getX());
        assertEquals(-2, root.getMin().getY());
        assertEquals(-2, root.getMin().getZ());
        assertEquals(2, root.getMax().getX());
        assertEquals(2, root.getMax().getY());
        assertEquals(2, root.getMax().getZ());
    }

    @Test
    void addingWithinRadiusOfFirstDoesNotExpand() {
        final var index = new SpatialPathIndex(2);
        index.insert(new Vector3(0, 0, 0), 0);
        final var root = index.getRootNode();

        index.insert(new Vector3(1, 1, 1), 1);

        assertEquals(root, index.getRootNode());
    }

    @Test
    void addingOutsideRadiusOfFirstCreatesNewRoot() {
        final var index = new SpatialPathIndex(2);
        index.insert(new Vector3(0, 0, 0), 0);
        final var root = index.getRootNode();

        index.insert(new Vector3(3, 1, 1), 1);

        assertNotEquals(root, index.getRootNode());
    }

    @Test
    void newRootHasTheOldRootAsChild() {
        final var index = new SpatialPathIndex(2);
        index.insert(new Vector3(0, 0, 0), 0);
        final var oldRoot = index.getRootNode();

        index.insert(new Vector3(3, 1, 1), 1);
        final var newRoot = index.getRootNode();

        assertTrue(Arrays.asList(newRoot.getChildren())
                         .contains(oldRoot));
    }

    @Test
    void addingOutsideRadiusOfFirstCreatesNewRootWithDepthOne() {
        final var index = new SpatialPathIndex(2);
        index.insert(new Vector3(0, 0, 0), 0);
        index.insert(new Vector3(3, 1, 1), 1);

        assertEquals(1, index.getRootNode().getDepth());
    }

    @Test
    void addingOutsideRadiusOfFirstCreatesNewRootWithExpectedSize() {
        final var index = new SpatialPathIndex(2);
        index.insert(new Vector3(0, 0, 0), 0);
        index.insert(new Vector3(3, 1, 1), 1);

        assertEquals(-2, index.getRootNode().getMin().getX());
        assertEquals(-2, index.getRootNode().getMin().getY());
        assertEquals(-2, index.getRootNode().getMin().getZ());
        assertEquals(6, index.getRootNode().getMax().getX());
        assertEquals(6, index.getRootNode().getMax().getY());
        assertEquals(6, index.getRootNode().getMax().getZ());
    }

    @Test
    void insertingPointsToAllDepthOneRootDoesNotExpandFurther() {
        final var index = new SpatialPathIndex(2);
        index.insert(new Vector3(0, 0, 0), 0);

        index.insert(new Vector3(3, 1, 1), 1);
        final var root = index.getRootNode();
        assertEquals(1, root.getDepth());

        index.insert(new Vector3(1, 3, 1), 1);
        index.insert(new Vector3(1, 1, 3), 1);
        index.insert(new Vector3(3, 3, 1), 1);
        index.insert(new Vector3(1, 3, 3), 1);
        index.insert(new Vector3(3, 1, 3), 1);
        index.insert(new Vector3(3, 3, 3), 1);

        assertEquals(-2, index.getRootNode().getMin().getX());
        assertEquals(-2, index.getRootNode().getMin().getY());
        assertEquals(-2, index.getRootNode().getMin().getZ());
        assertEquals(6, index.getRootNode().getMax().getX());
        assertEquals(6, index.getRootNode().getMax().getY());
        assertEquals(6, index.getRootNode().getMax().getZ());
        assertEquals(index.getRootNode(), root);
        assertEquals(1, index.getRootNode().getDepth());
    }

    @Test
    void noneOfTheNewRootChildrenAreNullAfterInsertingToAllChildBounds() {
        final var index = new SpatialPathIndex(2);
        index.insert(new Vector3(0, 0, 0), 0);

        index.insert(new Vector3(3, 1, 1), 1);
        index.insert(new Vector3(1, 3, 1), 1);
        index.insert(new Vector3(1, 1, 3), 1);
        index.insert(new Vector3(3, 3, 1), 1);
        index.insert(new Vector3(1, 3, 3), 1);
        index.insert(new Vector3(3, 1, 3), 1);
        index.insert(new Vector3(3, 3, 3), 1);
        final var root = index.getRootNode();

        assertTrue(Arrays.stream(root.getChildren())
                         .allMatch(Objects::nonNull));
    }

    @Test
    void depthOneChildrenHaveTheExpectedBounds() {
        final var index = new SpatialPathIndex(2);
        index.insert(new Vector3(0, 0, 0), 0);

        index.insert(new Vector3(3, 1, 1), 1);
        index.insert(new Vector3(1, 3, 1), 1);
        index.insert(new Vector3(1, 1, 3), 1);
        index.insert(new Vector3(3, 3, 1), 1);
        index.insert(new Vector3(1, 3, 3), 1);
        index.insert(new Vector3(3, 1, 3), 1);
        index.insert(new Vector3(3, 3, 3), 1);

        final var root = index.getRootNode();
        // 0-3 bottom layer     4-7 top layer
        final var c0 = root.getChild(0);
        final var c1 = root.getChild(1);
        final var c2 = root.getChild(2);
        final var c3 = root.getChild(3);
        final var c4 = root.getChild(4);
        final var c5 = root.getChild(5);
        final var c6 = root.getChild(6);
        final var c7 = root.getChild(7);

        assertEquals(-2, c0.getMin().getX());
        assertEquals(-2, c0.getMin().getY());
        assertEquals(-2, c0.getMin().getZ());
        assertEquals(2, c0.getMax().getX());
        assertEquals(2, c0.getMax().getY());
        assertEquals(2, c0.getMax().getZ());

        assertEquals(2, c1.getMin().getX());
        assertEquals(-2, c1.getMin().getY());
        assertEquals(-2, c1.getMin().getZ());
        assertEquals(6, c1.getMax().getX());
        assertEquals(2, c1.getMax().getY());
        assertEquals(2, c1.getMax().getZ());

        assertEquals(-2, c2.getMin().getX());
        assertEquals(-2, c2.getMin().getY());
        assertEquals(2, c2.getMin().getZ());
        assertEquals(2, c2.getMax().getX());
        assertEquals(2, c2.getMax().getY());
        assertEquals(6, c2.getMax().getZ());

        assertEquals(2, c3.getMin().getX());
        assertEquals(-2, c3.getMin().getY());
        assertEquals(2, c3.getMin().getZ());
        assertEquals(6, c3.getMax().getX());
        assertEquals(2, c3.getMax().getY());
        assertEquals(6, c3.getMax().getZ());

        assertEquals(-2, c4.getMin().getX());
        assertEquals(2, c4.getMin().getY());
        assertEquals(-2, c4.getMin().getZ());
        assertEquals(2, c4.getMax().getX());
        assertEquals(6, c4.getMax().getY());
        assertEquals(2, c4.getMax().getZ());

        assertEquals(2, c5.getMin().getX());
        assertEquals(2, c5.getMin().getY());
        assertEquals(-2, c5.getMin().getZ());
        assertEquals(6, c5.getMax().getX());
        assertEquals(6, c5.getMax().getY());
        assertEquals(2, c5.getMax().getZ());

        assertEquals(-2, c6.getMin().getX());
        assertEquals(2, c6.getMin().getY());
        assertEquals(2, c6.getMin().getZ());
        assertEquals(2, c6.getMax().getX());
        assertEquals(6, c6.getMax().getY());
        assertEquals(6, c6.getMax().getZ());

        assertEquals(2, c7.getMin().getX());
        assertEquals(2, c7.getMin().getY());
        assertEquals(2, c7.getMin().getZ());
        assertEquals(6, c7.getMax().getX());
        assertEquals(6, c7.getMax().getY());
        assertEquals(6, c7.getMax().getZ());
    }
}
