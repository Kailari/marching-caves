package caves.generator.mesh;

import caves.generator.CaveSampleSpace;
import caves.generator.PathGenerator;
import caves.util.math.LineSegment;
import caves.util.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
                    final var distance = (float) cavePath.getNodesWithin(pos, caveRadius)
                                                         .stream()
                                                         .filter(i -> cavePath.getPreviousFor(i).isPresent())
                                                         .map(i -> {
                                                             final var a = cavePath.get(i);
                                                             final var b = cavePath.get(cavePath.getPreviousFor(i)
                                                                                                .orElseThrow());
                                                             return LineSegment.closestPoint(a, b, pos, new Vector3());
                                                         })
                                                         .mapToDouble(pos::distance)
                                                         .min()
                                                         .orElse(caveRadius * caveRadius);
                    return Math.min(1.0f, distance / caveRadius);
                };
        final var sampleSpace = new CaveSampleSpace(cavePath,
                                                    maxInfluenceRadius,
                                                    samplesPerUnit,
                                                    densityFunction);

        final var meshGenerator = new MeshGenerator(sampleSpace);
        final var caveVertices = new ArrayList<Vector3>();
        final var caveIndices = new ArrayList<Integer>();
        final var caveNormals = new ArrayList<Vector3>();
        meshGenerator.generate(cavePath, caveVertices, caveNormals, caveIndices, surfaceLevel);

        assertAll(() -> assertEquals(24288, caveIndices.size()));
        assertAll(() -> assertEquals(24288, caveVertices.size()));
        assertAll(() -> assertEquals(caveVertices.size(), caveNormals.size()));
    }
}
