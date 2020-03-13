package caves;

import caves.visualization.Application;

public final class Main {
    private static final boolean VALIDATION = Boolean.parseBoolean(System.getProperty("vulkan.validation", "true"));

    private Main() {
    }

    /**
     * Application main entry-point.
     *
     * @param args un-parsed command-line arguments
     */
    public static void main(final String[] args) {
        System.out.println("Validation: " + VALIDATION);
        final var app = new Application(VALIDATION);
        app.run();
    }
}
