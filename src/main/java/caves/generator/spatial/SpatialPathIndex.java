package caves.generator.spatial;

import caves.util.collections.GrowingAddOnlyList;
import caves.util.math.BoundingBox;
import caves.util.math.Vector3;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * Implements an inverted octree as spatial index to the cave path. The idea is as follows:
 * <ol>
 *     <li>Buckets have fixed width of <code>2 * maxInfluenceRadius</code>. This means that any
 *     point gets contributions from exactly eight neighboring buckets at most <i>(There is an edge
 *     case where the point is exactly at center of a bucket, and will then get contributions from
 *     that bucket only)</i>.</li>
 *     <li>The fixed width <i>should</i> result in very fast queries, as most searches will use
 *     the max influence radius as query range.</li>
 *     <li>Bucket max size is not limited to allow arbitrary number of nodes per bucket. However,
 *     as path tends to not cross itself very often, this should result in maximum of 4-8 nodes per
 *     bucket in worst cases and about 2-3 per bucket on average.</li>
 *     <li>The twist is that the tree itself is constructed from leaves up, instead of doing it
 *     the other way around like we traditionally would.</li>
 *     <li>When we try to insert a node outside of the tree, we perform the split by
 *     <strong>expanding</strong> the tree; we create seven sibling nodes for our current root-level
 *     node and create a new root node around those</li>
 *     <li>Insertion to any existing node works like in traditional octree</li>
 *     <li>any nodes which do not yet contain anything can be initialized as <code>null</code> to
 *     avoid allocations</li>
 * </ol>
 * <p>
 * This effectively allows us to start creating the tree on node-by-node basis, without knowing the
 * exact bounds at the start. The tree has effectively infinite size, although limiting the depth is
 * likely desired or necessary at some point.
 * <p>
 * There is also the assumption that the path points themselves are immutable. Only indices to each
 * node are stored, making the underlying buckets relatively cheap memory-wise. To avoid having to
 * store positions, the tree is immediately split to maximum depth on insert. This imposes some
 * additional tree walking on read, but simplifies the implementation and reduces memory footprint.
 */
public class SpatialPathIndex {
    private final float maxInfluenceRadius;

    @Nullable
    private OctreeNode rootNode;

    /**
     * Constructs a new spatial index for fast node queries.
     *
     * @param maxInfluenceRadius maximum influence radius of a node.
     */
    public SpatialPathIndex(final float maxInfluenceRadius) {
        this.maxInfluenceRadius = maxInfluenceRadius;
    }

    /**
     * Inserts the given index. Expands the tree if necessary.
     *
     * @param position position of the node
     * @param index    index of the node
     */
    public void insert(final Vector3 position, final int index) {
        if (this.rootNode == null) {
            this.rootNode = OctreeNode.initialRoot(position, this.maxInfluenceRadius);
        }

        if (!this.rootNode.contains(position)) {
            expandTowards(position);
        }

        this.rootNode.insert(position, index);
    }

    private void expandTowards(final Vector3 position) {
        assert this.rootNode != null;

        // 1. figure out which direction we are expanding towards. This decides which one of
        //    the final eight nodes the current tree is treated as and what will be the bounds of
        //    the new root.
        var index = 0;
        final float minY;
        final float maxY;
        final var center = this.rootNode.getCenter();
        if (position.getY() >= center.getY()) {
            index += 4;
            minY = this.rootNode.getMin().getY();
            maxY = this.rootNode.getMax().getY() + this.rootNode.getSizeY();
        } else {
            minY = this.rootNode.getMin().getY() - this.rootNode.getSizeY();
            maxY = this.rootNode.getMax().getY();
        }

        final float minZ;
        final float maxZ;
        if (position.getZ() >= center.getZ()) {
            index += 2;
            minZ = this.rootNode.getMin().getZ();
            maxZ = this.rootNode.getMax().getZ() + this.rootNode.getSizeZ();
        } else {
            minZ = this.rootNode.getMin().getZ() - this.rootNode.getSizeZ();
            maxZ = this.rootNode.getMax().getZ();
        }

        final float minX;
        final float maxX;
        if (position.getX() >= center.getX()) {
            index += 1;
            minX = this.rootNode.getMin().getX();
            maxX = this.rootNode.getMax().getX() + this.rootNode.getSizeX();
        } else {
            minX = this.rootNode.getMin().getX() - this.rootNode.getSizeX();
            maxX = this.rootNode.getMax().getX();
        }

        // 2. create the new root node with current tree as one of the children; other siblings can
        //    be null at this point as they will be initialized on demand
        final var siblings = new OctreeNode[8];
        final var oldRoot = this.rootNode;
        siblings[index] = oldRoot;
        this.rootNode = OctreeNode.expandRoot(this.rootNode.getDepth() + 1,
                                              siblings,
                                              new Vector3(minX, minY, minZ),
                                              new Vector3(maxX, maxY, maxZ));
    }

    /**
     * Gets all indices that are inside the given radius from the given position. This is a rough
     * estimate with errors up to <code>maxInfluenceRadius</code> in worst case. A lot better than
     * iterating O(n) through all nodes, though.
     *
     * @param position position to query from
     * @param radius   maximum distance to any given point
     *
     * @return all indices of points within the given radius
     */
    public Collection<Integer> getIndicesWithin(
            final Vector3 position,
            final double radius
    ) {
        if (this.rootNode == null) {
            return List.of();
        }

        return this.rootNode.getInfluencingPoints(position, radius);
    }

    /**
     * Represents a collection of data points with defined boundaries in space. A single node in the
     * octree.
     */
    private static final class OctreeNode extends BoundingBox {
        private final int depth;

        @Nullable private final Collection<Integer> items;
        @Nullable private final OctreeNode[] children;

        public int getDepth() {
            return this.depth;
        }

        private OctreeNode(
                final int depth,
                final Vector3 min,
                final Vector3 max,
                @Nullable final OctreeNode[] children
        ) {
            super(min, max);
            this.depth = depth;
            this.items = this.depth == 0 ? new GrowingAddOnlyList<>(Integer.class, 1) : null;
            this.children = children;
        }

        public static OctreeNode initialRoot(
                final Vector3 position,
                final float margin
        ) {
            return new OctreeNode(0,
                                  position.sub(margin, margin, margin, new Vector3()),
                                  position.add(margin, margin, margin, new Vector3()),
                                  null);
        }

        public static OctreeNode expandRoot(
                final int depth,
                final OctreeNode[] children,
                final Vector3 min,
                final Vector3 max
        ) {
            return new OctreeNode(depth, min, max, children);
        }

        public void insert(final Vector3 position, final int index) {
            // As the tree is always immediately split to max depth on node creation, there are only
            // two options we might want to do here:
            //  1. this is a leaf node, add the index
            //  2. this is a non-leaf node, find correct child and insert there

            if (this.depth == 0) {
                assert this.items != null;
                this.items.add(index);
            } else {
                assert this.children != null;
                getOrCreateChildAt(position).insert(position, index);
            }
        }

        public OctreeNode getOrCreateChildAt(final Vector3 position) {
            assert this.children != null;
            assert this.depth > 0;

            // Avoid calculating the center multiple times by caching the result
            final var center = getCenter();

            var childIndex = 0;
            final float minY;
            final float maxY;
            if (position.getY() >= center.getY()) {
                childIndex += 4;
                minY = center.getY();
                maxY = getMax().getY();
            } else {
                minY = getMin().getY();
                maxY = center.getY();
            }

            final float minZ;
            final float maxZ;
            if (position.getZ() >= center.getZ()) {
                childIndex += 2;
                minZ = center.getZ();
                maxZ = getMax().getZ();
            } else {
                minZ = getMin().getZ();
                maxZ = center.getZ();
            }

            final float minX;
            final float maxX;
            if (position.getX() >= center.getX()) {
                childIndex += 1;
                minX = center.getX();
                maxX = getMax().getX();
            } else {
                minX = getMin().getX();
                maxX = center.getX();
            }

            return getOrCreateChild(childIndex, minY, maxY, minZ, maxZ, minX, maxX);
        }

        public OctreeNode getOrCreateChild(
                final int childIndex,
                final float minY,
                final float maxY,
                final float minZ,
                final float maxZ,
                final float minX,
                final float maxX
        ) {
            assert this.children != null;

            if (this.children[childIndex] == null) {
                final var childDepth = this.depth - 1;
                this.children[childIndex] = new OctreeNode(childDepth,
                                                           new Vector3(minX, minY, minZ),
                                                           new Vector3(maxX, maxY, maxZ),
                                                           childDepth != 0 ? new OctreeNode[8] : null);
            }

            return this.children[childIndex];
        }

        public Collection<Integer> getInfluencingPoints(
                final Vector3 position,
                final double maxInfluenceRadius
        ) {
            return getInfluencingPoints(position,
                                        maxInfluenceRadius,
                                        new GrowingAddOnlyList<>(Integer.class, 40));
        }

        public Collection<Integer> getInfluencingPoints(
                final Vector3 position,
                final double maxInfluenceRadius,
                final Collection<Integer> result
        ) {
            if (this.depth == 0) {
                assert this.items != null;
                // XXX: We do not actually filter by position here as *we only know the index*
                result.addAll(this.items);
                return result;
            }

            assert this.children != null;
            for (final var child : this.children) {
                if (child == null) {
                    continue;
                }

                if (child.intersectsSphere(position, maxInfluenceRadius)) {
                    child.getInfluencingPoints(position, maxInfluenceRadius, result);
                }
            }

            return result;
        }

        private boolean intersectsSphere(
                final Vector3 position,
                final double maxInfluenceRadius
        ) {
            float d = 0.0f;
            if (position.getX() < getMin().getX()) {
                d += square(position.getX() - getMin().getX());
            } else if (position.getX() > getMax().getX()) {
                d += square(position.getX() - getMax().getX());
            }

            if (position.getY() < getMin().getY()) {
                d += square(position.getY() - getMin().getY());
            } else if (position.getY() > getMax().getY()) {
                d += square(position.getY() - getMax().getY());
            }

            if (position.getZ() < getMin().getZ()) {
                d += square(position.getZ() - getMin().getZ());
            } else if (position.getZ() > getMax().getZ()) {
                d += square(position.getZ() - getMax().getZ());
            }

            return d <= square((float) maxInfluenceRadius);
        }

        private float square(final float v) {
            return v * v;
        }
    }
}
