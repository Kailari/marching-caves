package caves.generator.density;

import caves.util.math.Vector3;

public interface DensityFunction {
    /**
     * Gets the density at the given position.
     *
     * @param position the position
     *
     * @return the density
     */
    float apply(Vector3 position);
}
