package caves.generator.mesh;

import caves.generator.CaveSampleSpace;
import caves.generator.ChunkCaveSampleSpace;
import caves.util.math.Vector3;

import java.util.Collection;

public final class MarchingCubes {
    /** How close two samples' values need to be in order to be considered the same value. */
    private static final float SAME_POINT_EPSILON = 0.0001f;

    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;
    private static final int NUM_CUBE_VERTS = 8;

    private MarchingCubes() {
    }

    /**
     * Appends a new cube to the given mesh. Gets "free" faces of the cube as return value, for
     * progressing the flood fill.
     *
     * @param outVertices  vertices of the mesh
     * @param outNormals   vertex normals of the mesh
     * @param outIndices   indices of the mesh
     * @param sampleSpace  sample space to use for generation
     * @param x            x-coordinate to sample
     * @param y            y-coordinate to sample
     * @param z            z-coordinate to sample
     * @param surfaceLevel surface level
     *
     * @return free faces of the created cube
     */
    public static MarchingCubesTables.Facing[] appendToMesh(
            final Collection<Vector3> outVertices,
            final Collection<Vector3> outNormals,
            final Collection<Integer> outIndices,
            final ChunkCaveSampleSpace sampleSpace,
            final int x,
            final int y,
            final int z,
            final float surfaceLevel
    ) {
        final float[] density = new float[NUM_CUBE_VERTS];
        for (int i = 0; i < NUM_CUBE_VERTS; ++i) {
            density[i] = sampleSpace.getDensity(x + MarchingCubesTables.VERTEX_OFFSETS[i][X],
                                                y + MarchingCubesTables.VERTEX_OFFSETS[i][Y],
                                                z + MarchingCubesTables.VERTEX_OFFSETS[i][Z]);
        }
        final var cubeIndex = MarchingCubesTables.calculateCubeIndex(surfaceLevel, density);
        final var edgeMask = MarchingCubesTables.EDGE_TABLE[cubeIndex];
        if (edgeMask == 0) {
            // Still need to check for free faces as this still may be either empty (all free) or
            // filled (none free) cube, giving us two very distinct cases during the flood fill.
            return MarchingCubesTables.FREE_CUBE_FACES[cubeIndex];
        }

        final Vector3[] pos = new Vector3[NUM_CUBE_VERTS];
        for (int i = 0; i < NUM_CUBE_VERTS; ++i) {
            pos[i] = sampleSpace.getPos(x + MarchingCubesTables.VERTEX_OFFSETS[i][X],
                                        y + MarchingCubesTables.VERTEX_OFFSETS[i][Y],
                                        z + MarchingCubesTables.VERTEX_OFFSETS[i][Z]);
        }

        // We have positions and densities for cube corners, calculate estimated surface gradient
        // vectors for them
        final Vector3[] gradient = new Vector3[NUM_CUBE_VERTS];
        for (var i = 0; i < NUM_CUBE_VERTS; ++i) {
            gradient[i] = calculateGradientVector(sampleSpace,
                                                  x + MarchingCubesTables.VERTEX_OFFSETS[i][X],
                                                  y + MarchingCubesTables.VERTEX_OFFSETS[i][Y],
                                                  z + MarchingCubesTables.VERTEX_OFFSETS[i][Z]);
        }

        // Create edge vertices (vertex is not created if not needed). Here, we are adding vertices
        // to the MIDDLE POINTS of cube edges. That is, there are twelve different middle-points we
        // could possibly need. The `edgeMask` is a 12bit bit-mask with each bit signifying a single
        // edge vertex. By knowing the edge bit order and positions of the eight corners, it becomes
        // trivial lookup to place vertices at correct middle-points.
        //
        // Furthermore, as we know that the isosurface cuts the edge, likely not at the middle-point,
        // but at some point between the vertices of the edge, we can interpolate positions based on
        // delta values between the densities at the corners and the surface level to get more
        // accurate estimate of the surface.
        //
        // Edges ordered by index: (i  from -> to)
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
        final var normals = new Vector3[maxVerticesPerCube];
        for (var i = 0; i < 4; i++) {
            // Lower/Upper
            final var lowerOffset = 0;
            final var upperOffset = 4;
            handleLayerVertex(surfaceLevel, density, edgeMask, pos, gradient, vertices, normals, i, lowerOffset);
            handleLayerVertex(surfaceLevel, density, edgeMask, pos, gradient, vertices, normals, i, upperOffset);

            // Vertical
            final var verticalOffset = upperOffset + 4;
            if ((edgeMask & (1 << (verticalOffset + i))) != 0) {
                final var ia = lowerOffset + i;
                final var ib = upperOffset + i;
                vertices[verticalOffset + i] = interpolateToSurface(surfaceLevel,
                                                                    pos[ia], pos[ib],
                                                                    density[ia], density[ib]);
                normals[verticalOffset + i] = interpolateToSurface(surfaceLevel,
                                                                   gradient[ia], gradient[ib],
                                                                   density[ia], density[ib]);
            }
        }

        // Now, we have a collection of vertices, but we do not know which vertices were created nor
        // how they should be connected. Luckily, this is again one of the 256 pre-defined cases, so
        // just use a lookup table. As we are using triangles, there are three vertices per each
        // triangular polygon.
        final var vertexIndexLookup = MarchingCubesTables.TRIANGULATION_TABLE[cubeIndex];
        final var verticesPerPolygon = 3;
        for (var i = 0; i < vertexIndexLookup.length; i += verticesPerPolygon) {
            final var baseIndex = outVertices.size();
            for (var j = 0; j < verticesPerPolygon; ++j) {
                final var vertexIndex = vertexIndexLookup[i + j];

                assert vertices[vertexIndex] != null : "Vertex cannot be null! Invalid triangulation list or vertices were populated incorrectly!";
                assert normals[vertexIndex] != null : "Normal cannot be null! Normals were populated incorrectly!";

                outVertices.add(vertices[vertexIndex]);
                outNormals.add(normals[vertexIndex]);
                outIndices.add(baseIndex + j);
            }
        }

        return MarchingCubesTables.FREE_CUBE_FACES[cubeIndex];
    }

    private static Vector3 calculateGradientVector(
            final ChunkCaveSampleSpace samples,
            final int x,
            final int y,
            final int z
    ) {
        // Estimate derivative of the density function using central differences
        final var gx = (samples.getDensity(x + 1, y, z) - samples.getDensity(x - 1, y, z));
        final var gy = (samples.getDensity(x, y + 1, z) - samples.getDensity(x, y - 1, z));
        final var gz = (samples.getDensity(x, y, z + 1) - samples.getDensity(x, y, z - 1));
        final var grad = new Vector3(gx, gy, gz);
        if (grad.lengthSq() > 0.0f) {
            grad.normalize();
        }

        return grad;
    }

    private static void handleLayerVertex(
            final float surfaceLevel,
            final float[] density,
            final int edgeMask,
            final Vector3[] pos,
            final Vector3[] gradient,
            final Vector3[] vertices,
            final Vector3[] normals,
            final int i,
            final int layerOffset
    ) {
        if ((edgeMask & (1 << (layerOffset + i))) != 0) {
            final var ia = layerOffset + i;
            final var ib = layerOffset + ((i + 1) % 4);
            vertices[layerOffset + i] = interpolateToSurface(surfaceLevel, pos[ia], pos[ib], density[ia], density[ib]);
            normals[layerOffset + i] = interpolateToSurface(surfaceLevel,
                                                            gradient[ia], gradient[ib],
                                                            density[ia], density[ib]);
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

        final var alpha = Math.min(1.0f, Math.max(0.0f, (surfaceLevel - valueA) / (valueB - valueA)));
        return Vector3.lerp(posA, posB, alpha, new Vector3());
    }
}
