package caves;

import caves.window.DeviceContext;
import caves.window.Window;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkQueue;

import java.nio.ByteBuffer;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.vulkan.VK10.vkGetDeviceQueue;

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
            final var deviceContext = window.getDeviceContext();
            final var graphicsQueue = getGraphicsQueue(deviceContext);
            final var presentationQueue = getPresentationQueue(deviceContext);

            window.show();
            while (!window.shouldClose()) {
                glfwPollEvents();
            }
            System.out.println("Finished.");
        }
    }

    private static VkQueue getGraphicsQueue(final DeviceContext deviceContext) {
        return getQueue(deviceContext.getDevice(), deviceContext.getGraphicsQueueFamilyIndex());
    }

    private static VkQueue getPresentationQueue(final DeviceContext deviceContext) {
        return getQueue(deviceContext.getDevice(), deviceContext.getPresentationQueueFamilyIndex());
    }

    private static VkQueue getQueue(final VkDevice device, final int queueFamilyIndex) {
        try (var stack = stackPush()) {
            final var pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device, queueFamilyIndex, 0, pQueue);
            return new VkQueue(pQueue.get(0), device);
        }
    }
}
