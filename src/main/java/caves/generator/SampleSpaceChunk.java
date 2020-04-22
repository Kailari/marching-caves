package caves.generator;

import caves.util.collections.VertexArray;
import caves.util.math.Vector3;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static caves.generator.ChunkCaveSampleSpace.CHUNK_SIZE;

public final class SampleSpaceChunk {
    private static final int INITIAL_VERTEX_CAPACITY = 6000;

    private final float surfaceLevel;
    private final AtomicInteger nQueued = new AtomicInteger(0);
    private final AtomicInteger solidSampleCount = new AtomicInteger(0);
    @Nullable private boolean[] queued;
    @Nullable private float[] samples;
    @Nullable private VertexArray<Vertex> vertices;

    /**
     * Gets the generated vertices for this chunk. This array is populated during generation in
     * {@link caves.generator.mesh.MeshGenerator MeshGenerator}
     *
     * @return vertices
     */
    @Nullable
    public VertexArray<Vertex> getVertices() {
        return this.vertices;
    }

    /**
     * For "lazily" creating the vertex array. This avoid needlessly allocating huge vertex arrays
     * for empty chunks.
     *
     * @return vertex position array for this chunk
     */
    public VertexArray<Vertex> getOrCreateVertices() {
        if (this.vertices == null) {
            this.vertices = new VertexArray<>(INITIAL_VERTEX_CAPACITY);
        }

        return this.vertices;
    }

    /**
     * Heuristic check for "readiness" of this chunk. Chunks considered ready are selected for mesh
     * (re-)generation.
     *
     * @return <code>true</code> if this chunk is ready for mesh generation
     */
    public boolean isReady() {
        return this.nQueued.get() == 0;
    }

    /**
     * Checks whether or not this chunk has any solid samples. This is used to compact empty chunks
     * out of the sample space.
     *
     * @return <code>true</code> if this chunk has at least one solid sample
     */
    public boolean isEmpty() {
        return this.solidSampleCount.get() == 0;
    }

    /**
     * Creates a new sample space for the given region of space.
     *
     * @param surfaceLevel the isosurface density level
     */
    public SampleSpaceChunk(final float surfaceLevel) {
        this.surfaceLevel = surfaceLevel;
    }

    private synchronized void lazyInitSamples() {
        if (this.samples != null) {
            return;
        }

        final var sampleCount = CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE;
        this.samples = new float[sampleCount];
        this.queued = new boolean[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            this.samples[i] = Float.NaN;
            this.queued[i] = false;
        }
    }

    /**
     * Calculates sample index for the given per-axis sample indices.
     *
     * @param x index of the sample on the x-axis
     * @param y index of the sample on the y-axis
     * @param z index of the sample on the z-axis
     *
     * @return the sample index
     */
    public int getSampleIndex(final int x, final int y, final int z) {
        final var localX = Math.abs(x % CHUNK_SIZE);
        final var localY = Math.abs(y % CHUNK_SIZE);
        final var localZ = Math.abs(z % CHUNK_SIZE);
        return localX + localZ * CHUNK_SIZE + localY * CHUNK_SIZE * CHUNK_SIZE;
    }

    /**
     * Calculates density for the sample at given per-axis sample indices.
     *
     * @param x               index of the sample on the x-axis
     * @param y               index of the sample on the y-axis
     * @param z               index of the sample on the z-axis
     * @param densityFunction the density function
     *
     * @return the density of the sample
     */
    public float getDensity(
            final int x,
            final int y,
            final int z,
            final Supplier<Float> densityFunction
    ) {
        lazyInitSamples();
        assert this.samples != null;

        final var sampleIndex = getSampleIndex(x, y, z);
        if (Float.isNaN(this.samples[sampleIndex])) {
            final var sample = densityFunction.get();
            this.samples[sampleIndex] = sample;

            if (sample < this.surfaceLevel) {
                this.solidSampleCount.incrementAndGet();
            }
        }

        return this.samples[sampleIndex];
    }

    /**
     * Marks the given sample as queued. If sample is not yet queued, increments the queue counter
     * by one.
     *
     * @param x x-coordinate of the sample
     * @param y y-coordinate of the sample
     * @param z z-coordinate of the sample
     *
     * @return <code>true</code> if the sample was queued, <code>false</code> if was already in
     *         queue
     */
    public boolean markQueued(final int x, final int y, final int z) {
        final var sampleIndex = getSampleIndex(x, y, z);

        lazyInitSamples();
        assert this.queued != null;

        // SAFETY: This is safe as this.queued is effectively final after lazy initialization
        //noinspection SynchronizeOnNonFinalField
        synchronized (this.queued) {
            if (this.queued[sampleIndex]) {
                return false;
            }

            this.nQueued.incrementAndGet();
            this.queued[sampleIndex] = true;
            return true;
        }
    }

    /**
     * Marks the given sample as popped from queue. Decrements the queue counter.
     *
     * @param x x-coordinate of the sample
     * @param y y-coordinate of the sample
     * @param z z-coordinate of the sample
     */
    public void popQueued(final int x, final int y, final int z) {
        assert this.nQueued.get() > 0;

        lazyInitSamples();
        assert this.queued != null;
        assert this.queued[getSampleIndex(x, y, z)];

        // SAFETY: This is safe as this.queued is effectively final after lazy initialization
        //noinspection SynchronizeOnNonFinalField
        synchronized (this.queued) {
            this.nQueued.decrementAndGet();
        }
    }

    public static final class Vertex {
        private final Vector3 position;
        private final Vector3 normal;

        /**
         * Gets the world position for this vertex.
         *
         * @return the position
         */
        public Vector3 getPosition() {
            return this.position;
        }

        /**
         * Gets the vertex normal at this vertex.
         *
         * @return the normal
         */
        public Vector3 getNormal() {
            return this.normal;
        }

        /**
         * Constructs a new vertex with given position and normal.
         *
         * @param position vertex position
         * @param normal   vertex normal
         */
        public Vertex(final Vector3 position, final Vector3 normal) {
            this.position = new Vector3(position);
            this.normal = new Vector3(normal);
        }
    }
}
