package caves.util.io;

import org.lwjgl.PointerBuffer;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class BufferUtil {
    private BufferUtil() {
    }

    /**
     * Iterates over a pointer buffer, assuming all elements are valid null-terminated UTF8
     * strings.
     *
     * @param buffer buffer to iterate
     * @param action action to perform on elements
     */
    public static void forEachAsStringUTF8(
            final PointerBuffer buffer,
            final Consumer<String> action
    ) {
        buffer.mark();
        while (buffer.hasRemaining()) {
            action.accept(buffer.getStringUTF8());
        }
        buffer.reset();
    }

    /**
     * Iterates over a pointer buffer, assuming all elements are valid null-terminated UTF8 strings
     * and checks if a predicate condition is fulfilled.
     *
     * @param buffer    buffer to iterate
     * @param condition condition that must be fulfilled
     * @param action    action to perform on the failing element
     *
     * @return <code>true</code> if any elements passed the test, <code>false</code> otherwise
     */
    public static boolean filteredForEachAsStringUTF8(
            final PointerBuffer buffer,
            final Predicate<String> condition,
            final Consumer<String> action
    ) {
        var result = false;
        buffer.mark();
        while (buffer.hasRemaining()) {
            final var next = buffer.getStringUTF8();
            if (condition.test(next)) {
                action.accept(next);
                result = true;
            }
        }
        buffer.reset();

        return result;
    }
}
