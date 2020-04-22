package caves.generator.mesh;

import caves.generator.ChunkCaveSampleSpace;
import caves.generator.PathGenerator;
import caves.util.math.LineSegment;
import caves.util.math.Vector3;
import caves.util.profiler.Profiler;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TestMarchingCubes {
    @Test
    void outputsExpectedAmountOfVerticesAndIndices() {
        final var caveLength = 10;
        final var spacing = 10f;
        final var surfaceLevel = 0.75f;
        final var spaceBetweenSamples = 2.0f;
        final var caveRadius = 20.0f;

        final var start = new Vector3(0.0f, 0.0f, 0.0f);
        final var maxInfluenceRadius = caveRadius + spaceBetweenSamples * 4;
        final var cavePath = new PathGenerator().generate(start, caveLength, spacing, maxInfluenceRadius, 420);
        final var samplesPerUnit = 1.0f / spaceBetweenSamples;
        final Function<Vector3, Float> densityFunction =
                (pos) -> {
                    final var distance = (float) Arrays.stream(cavePath.getNodesWithin(pos, caveRadius))
                                                       .filter(i -> cavePath.getPreviousFor(i) != -1)
                                                       .mapToObj(i -> {
                                                           final var a = cavePath.get(i);
                                                           final var b = cavePath.get(cavePath.getPreviousFor(i));
                                                           return LineSegment.closestPoint(a, b, pos, new Vector3());
                                                       })
                                                       .mapToDouble(pos::distance)
                                                       .min()
                                                       .orElse(caveRadius * caveRadius);
                    return Math.min(1.0f, distance / caveRadius);
                };
        final var sampleSpace = new ChunkCaveSampleSpace(samplesPerUnit, densityFunction, surfaceLevel);

        final var latch = new CountDownLatch(1);
        final var meshGenerator = new MeshGenerator(sampleSpace);
        meshGenerator.generate(cavePath, surfaceLevel,
                               (x, y, z, chunk) -> {},
                               () -> {
                                   latch.countDown();
                                   Profiler.PROFILER.log("Ready.");
                               });

        try {
            if (!latch.await(10000, TimeUnit.MILLISECONDS)) {
                fail("Timed out");
            }
        } catch (final InterruptedException ignored) {
        }

        final var counter = new AtomicInteger(0);
        sampleSpace.getChunks().forEach(chunk -> {
            final var cVerts = chunk.getVertices();
            if (cVerts != null) {
                counter.addAndGet(cVerts.size());
            }
        });

        assertEquals(24912, counter.get(), 128);
    }
}
