package caves;

import caves.visualization.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final boolean VALIDATION = Boolean.parseBoolean(System.getProperty("vulkan.validation", "true"));

    private Main() {
    }

    /**
     * Application main entry-point.
     *
     * @param args un-parsed command-line arguments
     */
    public static void main(final String[] args) {
        LOG.info("Validation: {}", VALIDATION);
        try (var app = new Application(VALIDATION)) {
            app.run();
        }
        LOG.info("Finished.");
    }
}
