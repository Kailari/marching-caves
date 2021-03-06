package caves.generator.mesh;

import caves.generator.ChunkCaveSampleSpace;
import caves.generator.SampleSpaceChunk;
import caves.generator.density.DensityFunction;
import caves.util.ThreadedResourcePool;
import caves.util.math.Vector3;

public final class MarchingCubes {
    private static final int MAX_VERTICES_PER_CUBE = 12;
    private static final int NUM_CUBE_VERTS = 8;

    /** How close two samples' values need to be in order to be considered the same value. */
    private static final float SAME_POINT_EPSILON = 0.0001f;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;
    private static final ThreadedResourcePool<Temporaries> TMP_POOL = new ThreadedResourcePool<>(Temporaries::new);

    private MarchingCubes() {
    }

    /**
     * Appends a new cube to the given mesh. Gets "free" faces of the cube as return value, for
     * progressing the flood fill.
     *
     * @param sampleSpace  sample space to use for generation
     * @param x            x-coordinate to sample
     * @param y            y-coordinate to sample
     * @param z            z-coordinate to sample
     * @param surfaceLevel surface level
     *
     * @return free faces of the created cube
     */
    public static MarchingCubesTables.Facing[] appendToMesh(
            final ChunkCaveSampleSpace sampleSpace,
            final int x,
            final int y,
            final int z,
            final float surfaceLevel
    ) {
        final var tmp = TMP_POOL.get();
        for (int i = 0; i < NUM_CUBE_VERTS; ++i) {
            tmp.densities[i] = sampleSpace.getDensity(x + MarchingCubesTables.VERTEX_OFFSETS[i][X],
                                                      y + MarchingCubesTables.VERTEX_OFFSETS[i][Y],
                                                      z + MarchingCubesTables.VERTEX_OFFSETS[i][Z],
                                                      tmp.samplePos,
                                                      tmp.densityTmp);
        }
        final var cubeIndex = MarchingCubesTables.calculateCubeIndex(surfaceLevel, tmp.densities);
        final var edgeMask = MarchingCubesTables.EDGE_TABLE[cubeIndex];
        if (edgeMask == 0) {
            // Still need to check for free faces as this still may be either empty (all free) or
            // filled (none free) cube, giving us two very distinct cases during the flood fill.
            return MarchingCubesTables.FREE_CUBE_FACES[cubeIndex];
        }

        for (int i = 0; i < NUM_CUBE_VERTS; ++i) {
            tmp.positions[i] = sampleSpace.getPos(x + MarchingCubesTables.VERTEX_OFFSETS[i][X],
                                                  y + MarchingCubesTables.VERTEX_OFFSETS[i][Y],
                                                  z + MarchingCubesTables.VERTEX_OFFSETS[i][Z],
                                                  tmp.positions[i]);
        }

        // We have positions and densities for cube corners, calculate estimated surface gradient
        // vectors for them
        for (var i = 0; i < NUM_CUBE_VERTS; ++i) {
            tmp.gradients[i] = calculateGradientVector(sampleSpace,
                                                       x + MarchingCubesTables.VERTEX_OFFSETS[i][X],
                                                       y + MarchingCubesTables.VERTEX_OFFSETS[i][Y],
                                                       z + MarchingCubesTables.VERTEX_OFFSETS[i][Z],
                                                       tmp.gradients[i],
                                                       tmp.samplePos,
                                                       tmp.densityTmp);
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

        final var vertices = tmp.vertices;
        final var normals = tmp.normals;
        for (var i = 0; i < 4; i++) {
            // Lower/Upper
            final var lowerOffset = 0;
            final var upperOffset = 4;
            handleLayerVertex(surfaceLevel, tmp.densities, edgeMask, tmp.positions, tmp.gradients,
                              vertices, normals, i, lowerOffset);
            handleLayerVertex(surfaceLevel, tmp.densities, edgeMask, tmp.positions, tmp.gradients,
                              vertices, normals, i, upperOffset);

            // Vertical
            final var verticalOffset = upperOffset + 4;
            if ((edgeMask & (1 << (verticalOffset + i))) != 0) {
                final var ia = lowerOffset + i;
                final var ib = upperOffset + i;
                vertices[verticalOffset + i] = interpolateToSurface(surfaceLevel,
                                                                    tmp.positions[ia], tmp.positions[ib],
                                                                    tmp.densities[ia], tmp.densities[ib],
                                                                    vertices[verticalOffset + i]);
                normals[verticalOffset + i] = interpolateToSurface(surfaceLevel,
                                                                   tmp.gradients[ia], tmp.gradients[ib],
                                                                   tmp.densities[ia], tmp.densities[ib],
                                                                   normals[verticalOffset + i]);
            }
        }

        // Now, we have a collection of vertices, but we do not know which vertices were created nor
        // how they should be connected. Luckily, this is again one of the 256 pre-defined cases, so
        // just use a lookup table. As we are using triangles, there are three vertices per each
        // triangular polygon.
        final var vertexIndexLookup = MarchingCubesTables.TRIANGULATION_TABLE[cubeIndex];

        final var chunk = sampleSpace.getChunkAt(x, y, z);

        final var outVertices = chunk.getOrCreateVertices();
        final int offset = outVertices.append(vertexIndexLookup.length);

        for (int i = 0; i < vertexIndexLookup.length; i++) {
            final int vertexIndex = vertexIndexLookup[i];
            outVertices.set(offset + i,
                            new SampleSpaceChunk.Vertex(vertices[vertexIndex],
                                                        normals[vertexIndex]));
        }

        return MarchingCubesTables.FREE_CUBE_FACES[cubeIndex];
    }

    private static Vector3 calculateGradientVector(
            final ChunkCaveSampleSpace samples,
            final int x,
            final int y,
            final int z,
            final Vector3 result,
            final Vector3 tmpPos,
            final DensityFunction.Temporaries densityTmp
    ) {
        // Estimate derivative of the density function using central differences
        final var gx0 = samples.getDensity(x + 1, y, z, tmpPos, densityTmp);
        final var gy0 = samples.getDensity(x, y + 1, z, tmpPos, densityTmp);
        final var gz0 = samples.getDensity(x, y, z + 1, tmpPos, densityTmp);
        final var gx1 = samples.getDensity(x - 1, y, z, tmpPos, densityTmp);
        final var gy1 = samples.getDensity(x, y - 1, z, tmpPos, densityTmp);
        final var gz1 = samples.getDensity(x, y, z - 1, tmpPos, densityTmp);
        final var grad = result.set(gx0 - gx1, gy0 - gy1, gz0 - gz1);
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
            vertices[layerOffset + i] = interpolateToSurface(surfaceLevel,
                                                             pos[ia],
                                                             pos[ib],
                                                             density[ia],
                                                             density[ib],
                                                             vertices[layerOffset + i]);
            normals[layerOffset + i] = interpolateToSurface(surfaceLevel,
                                                            gradient[ia],
                                                            gradient[ib],
                                                            density[ia],
                                                            density[ib],
                                                            normals[layerOffset + i]);
        }
    }

    private static Vector3 interpolateToSurface(
            final float surfaceLevel,
            final Vector3 posA,
            final Vector3 posB,
            final float valueA,
            final float valueB,
            final Vector3 tmpResult
    ) {
        final var deltaA = Math.abs(surfaceLevel - valueA);
        final var deltaB = Math.abs(surfaceLevel - valueB);
        final var delta = Math.abs(valueA - valueB);

        if (delta < SAME_POINT_EPSILON || deltaA < SAME_POINT_EPSILON) {
            // Either the points are considered the same (either one is fine) or the point A is
            // already at surface level.
            return tmpResult.set(posA);
        } else if (deltaB < SAME_POINT_EPSILON) {
            // The point B is already at surface level
            return tmpResult.set(posB);
        }

        final var alpha = Math.min(1.0f, Math.max(0.0f, (surfaceLevel - valueA) / (valueB - valueA)));
        return Vector3.lerp(posA, posB, alpha, tmpResult);
    }

    private static class Temporaries {
        private final float[] densities = new float[NUM_CUBE_VERTS];
        private final Vector3[] positions = new Vector3[NUM_CUBE_VERTS];
        private final Vector3[] gradients = new Vector3[NUM_CUBE_VERTS];

        private final Vector3[] vertices = new Vector3[MAX_VERTICES_PER_CUBE];
        private final Vector3[] normals = new Vector3[MAX_VERTICES_PER_CUBE];

        private final Vector3 samplePos = new Vector3();

        private final DensityFunction.Temporaries densityTmp = new DensityFunction.Temporaries();

        Temporaries() {
            for (int i = 0; i < NUM_CUBE_VERTS; i++) {
                this.positions[i] = new Vector3();
                this.gradients[i] = new Vector3();
            }

            for (int i = 0; i < MAX_VERTICES_PER_CUBE; i++) {
                this.vertices[i] = new Vector3();
                this.normals[i] = new Vector3();
            }
        }
    }
}
