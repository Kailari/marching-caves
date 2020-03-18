package caves.generator.mesh;

import caves.generator.CaveSampleSpace;
import caves.generator.util.Vector3;

import java.util.Collection;

public final class MarchingCubes {
    /** How close two samples' values need to be in order to be considered the same value. */
    private static final float SAME_POINT_EPSILON = 0.0001f;

    private MarchingCubes() {
    }

    /**
     * Appends a new cube to the given mesh.
     *
     * @param outVertices  vertices of the mesh
     * @param outIndices   indices of the mesh
     * @param sampleSpace  sample space to use for generation
     * @param x            x-coordinate to sample
     * @param y            y-coordinate to sample
     * @param z            z-coordinate to sample
     * @param surfaceLevel surface level
     */
    public static void appendToMesh(
            final Collection<Vector3> outVertices,
            final Collection<Integer> outIndices,
            final CaveSampleSpace sampleSpace,
            final int x,
            final int y,
            final int z,
            final float surfaceLevel
    ) {
        assert x >= 0 && x < sampleSpace.getCountX() - 1 : "Sample x must be within [0, size(x) - 1 (=" + sampleSpace.getCountX() + ")], was " + x;
        assert y >= 0 && y < sampleSpace.getCountY() - 1 : "Sample y must be within [0, size(y) - 1 (=" + sampleSpace.getCountY() + ")], was " + y;
        assert z >= 0 && z < sampleSpace.getCountZ() - 1 : "Sample z must be within [0, size(z) - 1 (=" + sampleSpace.getCountZ() + ")], was " + z;

        final int[] index = {
                sampleSpace.getSampleIndex(x, y, z),
                sampleSpace.getSampleIndex(x + 1, y, z),
                sampleSpace.getSampleIndex(x + 1, y, z + 1),
                sampleSpace.getSampleIndex(x, y, z + 1),
                sampleSpace.getSampleIndex(x, y + 1, z),
                sampleSpace.getSampleIndex(x + 1, y + 1, z),
                sampleSpace.getSampleIndex(x + 1, y + 1, z + 1),
                sampleSpace.getSampleIndex(x, y + 1, z + 1),
        };

        final float[] density = new float[index.length];
        for (int i = 0; i < index.length; ++i) {
            density[i] = sampleSpace.getDensity(index[i]);
        }
        final var cubeIndex = MarchingCubesTables.calculateCubeIndex(surfaceLevel, density);
        final var edgeMask = MarchingCubesTables.EDGE_TABLE[cubeIndex];
        if (edgeMask == 0) {
            return;
        }

        final Vector3[] pos = new Vector3[index.length];
        for (int i = 0; i < index.length; ++i) {
            pos[i] = sampleSpace.getPos(index[i]);
        }

        // Create edge vertices (vertex is not created if not needed)
        // Cases:
        //  Lower layer     Upper layer     Vertical
        //   0  0 -> 1       4  4 -> 5       8  0 -> 4
        //   1  1 -> 2       5  5 -> 6       9  1 -> 5
        //   2  2 -> 3       6  6 -> 7      10  2 -> 6
        //   3  3 -> 0       7  7 -> 4      11  3 -> 7
        //
        // This allows us to go through all the cases with a for loop, wrapping the lower- and upper
        // layer indices with modulo four.
        //
        // The EDGE_TABLE values are ordered so that the bits specify edge presence in clock-wise
        // order and lower first, upper second and vertical last.
        //  1. lower layer (cases 0-3)
        //  2. upper layer (cases 4-7)
        //  3. vertical    (cases 8-11)

        final var maxVerticesPerCube = 12;
        final var vertices = new Vector3[maxVerticesPerCube];
        final var nCases = 4;
        for (var i = 0; i < nCases; i++) {
            // Lower/Upper
            final var lowerOffset = 0;
            final var upperOffset = 4;
            handleLayerVertex(surfaceLevel, density, edgeMask, pos, vertices, i, lowerOffset);
            handleLayerVertex(surfaceLevel, density, edgeMask, pos, vertices, i, upperOffset);

            // Vertical
            final var verticalOffset = upperOffset + 4;
            if ((edgeMask & (1 << (verticalOffset + i))) != 0) {
                final var ia = lowerOffset + i;
                final var ib = upperOffset + i;
                vertices[verticalOffset + i] = interpolateToSurface(surfaceLevel, pos[ia], pos[ib], density[ia], density[ib]);
            }
        }

        // Now, we have a collection of vertices, but we do not know which vertices were created nor
        // how they should be connected. Luckily, this is again one of the 256 pre-defined cases, so
        // just use a lookup table. As we are using triangles, there are three vertices per each
        // triangular polygon.
        final var polygonCornerIndexLookup = MarchingCubesTables.TRIANGULATION_TABLE[cubeIndex];
        final var verticesPerPolygon = 3;
        for (var i = 0; i < polygonCornerIndexLookup.length; i += verticesPerPolygon) {
            final var baseIndex = outVertices.size();
            for (var j = 0; j < verticesPerPolygon; ++j) {
                final var vertexIndex = polygonCornerIndexLookup[i + j];
                final var vertex = vertices[vertexIndex];
                assert vertex != null : "Cube index returned triangulation list pointing to null vertex!";

                outVertices.add(vertex);
                outIndices.add(baseIndex + j);
            }
        }
    }

    private static void handleLayerVertex(
            final float surfaceLevel,
            final float[] density,
            final int edgeMask,
            final Vector3[] pos,
            final Vector3[] vertices,
            final int i,
            final int layerOffset
    ) {
        if ((edgeMask & (1 << (layerOffset + i))) != 0) {
            final var nCases = 4;

            final var ia = layerOffset + i;
            final var ib = layerOffset + ((i + 1) % nCases);
            vertices[layerOffset + i] = interpolateToSurface(surfaceLevel, pos[ia], pos[ib], density[ia], density[ib]);
        }
    }

    private static Vector3 interpolateToSurface(
            final float surfaceLevel,
            final Vector3 posA,
            final Vector3 posB,
            final float valueA,
            final float valueB
    ) {
        final var deltaA = Math.abs(surfaceLevel - valueA);
        final var deltaB = Math.abs(surfaceLevel - valueB);
        final var delta = Math.abs(valueA - valueB);

        if (delta < SAME_POINT_EPSILON || deltaA < SAME_POINT_EPSILON) {
            // Either the points are considered the same (either one is fine) or the point A is
            // already at surface level.
            return new Vector3(posA);
        } else if (deltaB < SAME_POINT_EPSILON) {
            // The point B is already at surface level
            return new Vector3(posB);
        }

        final var alpha = (surfaceLevel - valueA) / (valueA - valueB);
        return Vector3.lerp(posA, posB, alpha, new Vector3());
    }
}
