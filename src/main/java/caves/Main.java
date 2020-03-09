package caves;

import caves.window.Window;

public final class Main {
    private Main() {
    }

    /**
     * Application main entry-point.
     *
     * @param args un-parsed command-line arguments
     */
    public static void main(final String[] args) {
        System.out.println("Hello World!");

        final var window = new Window(800, 600);
    }
}
