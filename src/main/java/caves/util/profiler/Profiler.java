package caves.util.profiler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Stack;
import java.util.function.BiConsumer;

public class Profiler {
    /**
     * Profiler instance.
     */
    public static final Profiler PROFILER = new Profiler();

    private static final Logger LOG = LoggerFactory.getLogger(Profiler.class);
    private final Stack<ProfilerEntry> timestamps = new Stack<>();

    /**
     * Starts a new profiling region. This is analogous to pushing stack. Each start must eventually
     * be followed by a call to {@link #end()}
     *
     * @param name name of the region
     */
    public void start(final String name) {
        log(name);
        this.timestamps.push(new ProfilerEntry(System.nanoTime(), name));
    }

    /**
     * Logs the given message at depth equal to the current profiler stack size.
     *
     * @param message message to log
     * @param args    formatting arguments
     */
    public void log(final String message, final Object... args) {
        logAtDepth(this.timestamps.size(), LOG::info, message, args);
    }

    /**
     * Logs the given error message at depth equal to the current profiler stack size.
     *
     * @param message message to log
     * @param args    formatting arguments
     */
    public void err(final String message, final String args) {
        logAtDepth(this.timestamps.size(), LOG::error, message, args);
    }

    /**
     * Logs the given message at the specified "depth" (number of indents).
     *
     * @param depth       depth to log at
     * @param loggingFunc function to log with
     * @param message     message to log
     * @param args        formatting arguments
     */
    private void logAtDepth(
            final int depth,
            final BiConsumer<String, Object[]> loggingFunc,
            final String message,
            final Object... args
    ) {
        loggingFunc.accept("\t".repeat(depth) + message, args);
    }

    /**
     * Ends a profiling region. This is analogous to popping the stack. This logs the duration of
     * the step.
     */
    public void end() {
        final var entry = this.timestamps.pop();
        final var elapsed = (System.nanoTime() - entry.timestamp) / 1_000_000_000.0;
        log("{}. Done in {}s",
            entry.name,
            String.format("%.3f", elapsed));
    }

    /**
     * Utility for {@link #end() ending} a region and {@link #start(String) starting} a new one in a
     * single call.
     *
     * @param name name of the new region
     */
    public void next(final String name) {
        end();
        start(name);
    }

    private static final class ProfilerEntry {
        private final long timestamp;
        private final String name;

        private ProfilerEntry(final long timestamp, final String name) {
            this.timestamp = timestamp;
            this.name = name;
        }
    }
}
