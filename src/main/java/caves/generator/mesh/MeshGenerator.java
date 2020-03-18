package caves.generator.mesh;

import caves.generator.CaveSampleSpace;
import caves.generator.util.Vector3;

import java.util.Collection;

public class MeshGenerator {
    private final CaveSampleSpace sampleSpace;

    public MeshGenerator(final CaveSampleSpace sampleSpace) {
        this.sampleSpace = sampleSpace;
    }

    public void generateMeshForRegion(
            final Collection<Vector3> outVertices,
            final Collection<Vector3> outNormals,
            final Collection<Integer> outIndices,
            final float surfaceLevel,
            final int startX,
            final int startY,
            final int startZ,
            final int endX,
            final int endY,
            final int endZ
    ) {
        for (int x = startX + 1; x < endX - 1; ++x) {
            for (int y = startY + 1; y < endY - 1; ++y) {
                for (int z = startZ + 1; z < endZ - 1; ++z) {
                    MarchingCubes.appendToMesh(outVertices, outNormals, outIndices,
                                               this.sampleSpace,
                                               x, y, z,
                                               surfaceLevel);
                }
            }
        }
    }
}
