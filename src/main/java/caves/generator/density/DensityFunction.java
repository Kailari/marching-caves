package caves.generator.density;

import caves.generator.spatial.SpatialPathIndex;
import caves.util.collections.IntList;
import caves.util.math.Vector3;

public interface DensityFunction {
    /**
     * Gets the density at the given position.
     *
     * @param position    the position
     * @param temporaries pooled temporary variables for the calculation
     *
     * @return the density
     */
    float apply(Vector3 position, Temporaries temporaries);

    final class Temporaries {
        public final NodeContribution edgeResult = new NodeContribution();
        public final NodeContribution tmpEdgeResult = new NodeContribution();
        public final Vector3 tmpResult = new Vector3();
        public final IntList foundNodes = new IntList();
        public final SpatialPathIndex.OctreeNode[] nodeQueue = new SpatialPathIndex.OctreeNode[64];
    }
}
