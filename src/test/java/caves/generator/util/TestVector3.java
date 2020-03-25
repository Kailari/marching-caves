package caves.generator.util;

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
}
