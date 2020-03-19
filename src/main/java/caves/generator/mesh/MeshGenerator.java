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
        for (int x = startX + 2; x < endX - 2; ++x) {
            for (int y = startY + 2; y < endY - 2; ++y) {
                for (int z = startZ + 2; z < endZ - 2; ++z) {
                    MarchingCubes.appendToMesh(outVertices, outNormals, outIndices,
                                               this.sampleSpace,
                                               x, y, z,
                                               surfaceLevel);
                }
            }
        }
    }
}
