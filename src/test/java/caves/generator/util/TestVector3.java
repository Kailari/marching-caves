package caves.generator.util;

import caves.util.math.IntVector3;
import caves.util.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestVector3 {
    @Test
    void getXReturnsCorrectValue() {
        final var vec = new Vector3(12f, 6f, 7f);
        assertEquals(12f, vec.getX());
    }

    @Test
    void getYReturnsCorrectValue() {
        final var vec = new Vector3(12f, 6f, 7f);
        assertEquals(6f, vec.getY());
    }

    @Test
    void getZReturnsCorrectValue() {
        final var vec = new Vector3(12f, 6f, 7f);
        assertEquals(7f, vec.getZ());
    }

    @Test
    void conversionFromIntVectorWorks() {
        final var source = new IntVector3(3, 4, 7);
        final var actual = new Vector3(source);
        assertAll(() -> assertEquals(source.getX(), actual.getX()),
                  () -> assertEquals(source.getY(), actual.getY()),
                  () -> assertEquals(source.getZ(), actual.getZ()));
    }

    @Test
    void absReturnsCorrectValues() {
        final var actual = new Vector3(-1, 2, -3).abs();
        assertAll(() -> assertEquals(1, actual.getX()),
                  () -> assertEquals(2, actual.getY()),
                  () -> assertEquals(3, actual.getZ()));
    }

    @Test
    void setModifiesTheVectorCorrectly() {
        final var vec = new Vector3(12f, 6f, 7f);
        vec.set(4f, 2f, 0f);
        assertAll(() -> assertEquals(4f, vec.getX()),
                  () -> assertEquals(2f, vec.getY()),
                  () -> assertEquals(0f, vec.getZ()));
    }

    @Test
    void crossProductGivesCorrectResults() {
        final var a = new Vector3(12f, 6f, 7f);
        final var b = new Vector3(4f, 7f, 18f);

        final var cross = a.cross(b, new Vector3());
        assertAll(() -> assertEquals(59, cross.getX()),
                  () -> assertEquals(-188, cross.getY()),
                  () -> assertEquals(60, cross.getZ()));
    }

    @Test
    void crossProductGivesCorrectResultsWhenResultIsStoredToSelf() {
        final var a = new Vector3(12f, 6f, 7f);
        final var b = new Vector3(4f, 7f, 18f);

        final var cross = a.cross(b, a);
        assertAll(() -> assertEquals(59, cross.getX()),
                  () -> assertEquals(-188, cross.getY()),
                  () -> assertEquals(60, cross.getZ()));
    }

    @Test
    void crossProductWithEqualVectorIsZero() {
        final var a = new Vector3(12f, 6f, 7f);
        final var b = new Vector3(12f, 6f, 7f);

        final var cross = a.cross(b, new Vector3());
        assertAll(() -> assertEquals(0, cross.getX()),
                  () -> assertEquals(0, cross.getY()),
                  () -> assertEquals(0, cross.getZ()));
    }

    @Test
    void distanceSqGivesCorrectResults() {
        final var a = new Vector3(12f, 6f, 7f);
        final var b = new Vector3(4f, 7f, 18f);
        assertEquals(186f, a.distanceSq(b));
    }

    @Test
    void distanceGivesCorrectResults() {
        final var a = new Vector3(12f, 6f, 7f);
        final var b = new Vector3(4f, 7f, 18f);
        assertEquals(13.638f, a.distance(b), 0.01f);
    }

    @Test
    void lengthSqGivesCorrectResults() {
        final var a = new Vector3(12f, 6f, 7f);
        assertEquals(229f, a.lengthSq());
    }

    @Test
    void lengthGivesCorrectResults() {
        final var a = new Vector3(12f, 6f, 7f);
        assertEquals(15.132f, a.length(), 0.001);
    }

    @Test
    void normalizeThrowsWhenLengthIsZero() {
        final var a = new Vector3(0f, 0f, 0f);
        assertThrows(ArithmeticException.class, a::normalize);
    }

    @Test
    void normalizeOutputsUnitVector() {
        final var a = new Vector3(420f, -321f, 123f);
        assertEquals(1.0f, a.normalize().length(), 0.001);
    }

    @Test
    void normalizeWithoutParamsModifiesOriginal() {
        final var original = new Vector3(420f, -321f, 123f);
        final var a = new Vector3(original);
        a.normalize();
        assertAll(() -> assertNotEquals(original.getX(), a.getX()),
                  () -> assertNotEquals(original.getY(), a.getY()),
                  () -> assertNotEquals(original.getZ(), a.getZ()));
    }

    @Test
    void normalizeWithParamsDoesNotModifyOriginal() {
        final var original = new Vector3(420f, -321f, 123f);
        final var a = new Vector3(original);
        a.normalize(new Vector3());
        assertAll(() -> assertEquals(original.getX(), a.getX()),
                  () -> assertEquals(original.getY(), a.getY()),
                  () -> assertEquals(original.getZ(), a.getZ()));
    }

    @Test
    void subtractionGivesCorrectResults() {
        final var a = new Vector3(12f, 6f, 7f);
        final var b = new Vector3(4f, 7f, 18f);
        assertEquals(new Vector3(8f, -1f, -11f), a.sub(b, a));
    }

    @Test
    void additionGivesCorrectResults() {
        final var a = new Vector3(12f, 6f, 7f);
        final var b = new Vector3(4f, 7f, 18f);
        assertEquals(new Vector3(16f, 13f, 25f), a.add(b, a));
    }

    @Test
    void scalarMultiplicationGivesCorrectResults() {
        final var a = new Vector3(12f, 6f, 7f);
        assertEquals(new Vector3(120f, 60f, 70f), a.mul(10.0f, a));
    }

    @Test
    void minGivesCorrectResults() {
        final var a = new Vector3(12f, 6f, 7f);
        final var b = new Vector3(4f, 7f, 18f);
        assertEquals(new Vector3(4f, 6f, 7f), a.min(b, a));
    }

    @Test
    void maxGivesCorrectResults() {
        final var a = new Vector3(12f, 6f, 7f);
        final var b = new Vector3(4f, 7f, 18f);
        assertEquals(new Vector3(12f, 7f, 18f), a.max(b, a));
    }

    @Test
    void dotProductGivesCorrectResults() {
        final var a = new Vector3(12f, 6f, 7f);
        final var b = new Vector3(4f, 7f, 18f);
        assertEquals(216.0f, a.dot(b));
    }

    @Test
    void lerpReturnsExpectedValue() {
        final var expected = new Vector3(3.24f, 21.86f, 11.34f);
        final var a = new Vector3(2f, 20f, 7f);
        final var b = new Vector3(4f, 23f, 14f);
        final var t = 0.62f;

        final var actual = Vector3.lerp(a, b, t, new Vector3());
        assertAll(() -> assertEquals(expected.getX(), actual.getX(), 0.001),
                  () -> assertEquals(expected.getY(), actual.getY(), 0.001),
                  () -> assertEquals(expected.getZ(), actual.getZ(), 0.001));
    }

    @Test
    void mulWithoutResultArgModifiesOriginal() {
        final var actual = new Vector3(1, 2, 3).mul(10);
        assertAll(() -> assertEquals(10, actual.getX()),
                  () -> assertEquals(20, actual.getY()),
                  () -> assertEquals(30, actual.getZ()));
    }

    @Test
    void addWithoutResultArgModifiesOriginalWithSeparateComponents() {
        final var actual = new Vector3(1, 2, 3).add(10, 10, 10);
        assertAll(() -> assertEquals(11, actual.getX()),
                  () -> assertEquals(12, actual.getY()),
                  () -> assertEquals(13, actual.getZ()));
    }

    @Test
    void subWithoutResultArgModifiesOriginalWithSeparateComponents() {
        final var actual = new Vector3(1, 2, 3).sub(10, 10, 10);
        assertAll(() -> assertEquals(-9, actual.getX()),
                  () -> assertEquals(-8, actual.getY()),
                  () -> assertEquals(-7, actual.getZ()));
    }

    @Test
    void addWithoutResultArgModifiesOriginalWithVectorArg() {
        final var actual = new Vector3(1, 2, 3).add(new Vector3(10, 10, 10));
        assertAll(() -> assertEquals(11, actual.getX()),
                  () -> assertEquals(12, actual.getY()),
                  () -> assertEquals(13, actual.getZ()));
    }

    @Test
    void subWithoutResultArgModifiesOriginalWithVectorArg() {
        final var actual = new Vector3(1, 2, 3).sub(new Vector3(10, 10, 10));
        assertAll(() -> assertEquals(-9, actual.getX()),
                  () -> assertEquals(-8, actual.getY()),
                  () -> assertEquals(-7, actual.getZ()));
    }
}
