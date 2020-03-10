package caves;

import caves.window.Window;

import java.nio.ByteBuffer;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.system.MemoryUtil.memUTF8;

public final class Main {
    private static final boolean VALIDATION = Boolean.parseBoolean(System.getProperty("vulkan.validation", "true"));
    private static final int DEFAULT_WINDOW_WIDTH = 800;
    private static final int DEFAULT_WINDOW_HEIGHT = 600;

    private Main() {
    }

    /**
     * Application main entry-point.
     *
     * @param args un-parsed command-line arguments
     */
    public static void main(final String[] args) {
        System.out.println("Validation: " + VALIDATION);
        final var validationLayers = VALIDATION
                ? new ByteBuffer[]
                {
                        memUTF8("VK_LAYER_LUNARG_standard_validation"),
                }
                : new ByteBuffer[0];

        try (var window = new Window(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT, validationLayers)) {
            window.show();
            while (!window.shouldClose()) {
                glfwPollEvents();
            }
            System.out.println("Finished.");
        }
    }
}
