package caves.generator.spatial;

import caves.util.ThreadedResourcePool;
import caves.util.collections.IntList;
import caves.util.math.BoundingBox;
import caves.util.math.Vector3;

import javax.annotation.Nullable;

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
public final class SpatialPathIndex {
    private static final ThreadedResourcePool<IntList> LIST_POOL = new ThreadedResourcePool<>(IntList::new);
    private static final ThreadedResourcePool<OctreeNode[]> NODE_POOL = new ThreadedResourcePool<>(() -> new OctreeNode[32]);

    private final float maxInfluenceRadius;

    @Nullable
    private OctreeNode rootNode;

    /**
     * Gets the current root node for this index.
     *
     * @return the root node
     */
    public OctreeNode getRootNode() {
        assert this.rootNode != null : "getRootNode called before inserting the first point!";
        return this.rootNode;
    }

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

        while (!this.rootNode.contains(position)) {
            expandTowards(position);
        }
        assert this.rootNode.contains(position);

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
            minY = this.rootNode.getMin().getY();
            maxY = this.rootNode.getMax().getY() + this.rootNode.getSizeY();
        } else {
            index += 4;
            minY = this.rootNode.getMin().getY() - this.rootNode.getSizeY();
            maxY = this.rootNode.getMax().getY();
        }

        final float minZ;
        final float maxZ;
        if (position.getZ() >= center.getZ()) {
            minZ = this.rootNode.getMin().getZ();
            maxZ = this.rootNode.getMax().getZ() + this.rootNode.getSizeZ();
        } else {
            index += 2;
            minZ = this.rootNode.getMin().getZ() - this.rootNode.getSizeZ();
            maxZ = this.rootNode.getMax().getZ();
        }

        final float minX;
        final float maxX;
        if (position.getX() >= center.getX()) {
            minX = this.rootNode.getMin().getX();
            maxX = this.rootNode.getMax().getX() + this.rootNode.getSizeX();
        } else {
            index += 1;
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
    public IntList getIndicesWithin(
            final Vector3 position,
            final double radius
    ) {
        final var foundItems = LIST_POOL.get();
        foundItems.clear();

        if (this.rootNode == null) {
            return foundItems;
        }

        // XXX: This blows up if there are more than nodeQueue.length nodes per bucket
        final var nodeQueue = NODE_POOL.get();
        var queuePointer = 0;
        nodeQueue[queuePointer] = this.rootNode;
        while (queuePointer >= 0) {
            final var next = nodeQueue[queuePointer];
            queuePointer--;

            if (next.depth == 0) {
                assert next.items != null;
                foundItems.addAll(next.items);
            } else {
                assert next.children != null;
                for (final var child : next.children) {
                    if (child != null && child.intersectsSphere(position, radius)) {
                        queuePointer++;
                        nodeQueue[queuePointer] = child;
                    }
                }
            }
        }

        return foundItems;
    }

    /**
     * Represents a collection of data points with defined boundaries in space. A single node in the
     * octree.
     */
    public static final class OctreeNode extends BoundingBox {
        private final int depth;

        @Nullable private final OctreeNode[] children;
        @Nullable private int[] items;

        /**
         * Gets the depth of this node. Value of zero means this node is a leaf.
         *
         * @return the node depth
         */
        public int getDepth() {
            return this.depth;
        }

        /**
         * Gets the children of this node. Might be null if this is a leaf node.
         *
         * @return the children
         */
        @Nullable
        public OctreeNode[] getChildren() {
            return this.children;
        }

        private OctreeNode(
                final int depth,
                final Vector3 min,
                final Vector3 max,
                @Nullable final OctreeNode[] children
        ) {
            super(min, max);
            this.depth = depth;
            this.items = this.depth == 0 ? new int[0] : null;
            this.children = children;
        }

        private static OctreeNode initialRoot(
                final Vector3 position,
                final float margin
        ) {
            return new OctreeNode(0,
                                  position.sub(margin, margin, margin, new Vector3()),
                                  position.add(margin, margin, margin, new Vector3()),
                                  null);
        }

        private static OctreeNode expandRoot(
                final int depth,
                final OctreeNode[] children,
                final Vector3 min,
                final Vector3 max
        ) {
            return new OctreeNode(depth, min, max, children);
        }

        /**
         * Gets the child with index. Indexing follows component-wise min first, in order X-Z-Y, Y
         * incrementing the index by 4, Z by 2 and X by 1. This makes e.g. indices 0-4 the bottom
         * layer and the indices 5-8 the top layer.
         *
         * @param childIndex index of the child
         *
         * @return thi child with given index
         */
        public OctreeNode getChild(final int childIndex) {
            assert this.children != null;
            return this.children[childIndex];
        }

        /**
         * Inserts a new point to this node. Recursively proceeds down to child nodes if non-leaf.
         *
         * @param position position to add
         * @param index    index of the point to be added
         */
        public void insert(final Vector3 position, final int index) {
            // As the tree is always immediately split to max depth on node creation, there are only
            // two options we might want to do here:
            //  1. this is a leaf node, add the index
            //  2. this is a non-leaf node, find correct child and insert there

            if (this.depth == 0) {
                assert this.items != null;
                final var newItems = new int[this.items.length + 1];
                System.arraycopy(this.items, 0, newItems, 0, this.items.length);
                this.items = newItems;
                this.items[this.items.length - 1] = index;
            } else {
                assert this.children != null;
                getOrCreateChildAt(position).insert(position, index);
            }
        }

        private OctreeNode getOrCreateChildAt(final Vector3 position) {
            assert position.getY() >= getMin().getY();
            assert position.getY() <= getMax().getY();
            assert position.getZ() >= getMin().getZ();
            assert position.getZ() <= getMax().getZ();
            assert position.getX() >= getMin().getX();
            assert position.getX() <= getMax().getX();
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

        private OctreeNode getOrCreateChild(
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

        private boolean intersectsSphere(
                final Vector3 position,
                final double maxInfluenceRadius
        ) {
            double d = square(maxInfluenceRadius);
            if (position.getX() < getMin().getX()) {
                d -= square(position.getX() - getMin().getX());
            } else if (position.getX() > getMax().getX()) {
                d -= square(position.getX() - getMax().getX());
            }

            if (position.getY() < getMin().getY()) {
                d -= square(position.getY() - getMin().getY());
            } else if (position.getY() > getMax().getY()) {
                d -= square(position.getY() - getMax().getY());
            }

            if (position.getZ() < getMin().getZ()) {
                d -= square(position.getZ() - getMin().getZ());
            } else if (position.getZ() > getMax().getZ()) {
                d -= square(position.getZ() - getMax().getZ());
            }

            return d > 0;
        }

        private double square(final double v) {
            return v * v;
        }
    }
}
