package caves.window;

import org.lwjgl.glfw.GLFWKeyCallback;

import java.nio.ByteBuffer;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

/**
 * Wrapper over GLFW window context. Handles vulkan instance initialization.
 */
public final class Window implements AutoCloseable {
    private final VulkanInstance instance;
    private final VulkanDebug debug;
    private final PhysicalDevice physicalDevice;
    private final DeviceAndGraphicsQueueFamily deviceAndGraphicsQueueFamily;
    private final long windowHandle;
    private final GLFWKeyCallback keyCallback;
    private final long surfaceHandle;

    /**
     * Initializes a GLFW window with a vulkan context.
     *
     * @param width            initial width of the window
     * @param height           initial height of the window
     * @param validationLayers enabled validation layers
     */
    public Window(final int width, final int height, final ByteBuffer[] validationLayers) {
        if (!glfwInit()) {
            throw new IllegalStateException("Initializing GLFW failed.");
        }
        if (!glfwVulkanSupported()) {
            throw new IllegalStateException("GLFW could not find Vulkan loader.");
        }

        final var requiredExtensions = glfwGetRequiredInstanceExtensions();
        if (requiredExtensions == null) {
            throw new IllegalStateException("Failed to find list of required Vulkan extensions!");
        }

        try (var stack = stackPush()) {
            // Initialize vulkan context
            this.instance = new VulkanInstance(requiredExtensions, validationLayers);
            this.debug = new VulkanDebug(this.instance);
            this.physicalDevice = new PhysicalDevice(this.instance);
            this.deviceAndGraphicsQueueFamily = new DeviceAndGraphicsQueueFamily(this.physicalDevice, validationLayers);

            // Initialize GLFW window
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
            this.windowHandle = glfwCreateWindow(width, height, "Marching Caves", NULL, NULL);
            this.keyCallback = new GLFWKeyCallback() {
                @Override
                public void invoke(
                        final long window,
                        final int key,
                        final int scancode,
                        final int action,
                        final int mods
                ) {
                    if (key == GLFW_KEY_ESCAPE && action != GLFW_RELEASE) {
                        glfwSetWindowShouldClose(Window.this.windowHandle, true);
                    }
                }
            };
            glfwSetKeyCallback(this.windowHandle, this.keyCallback);
            final var pSurface = stack.mallocLong(1);
            final var error = glfwCreateWindowSurface(this.instance.getVkInstance(),
                                                      this.windowHandle,
                                                      null,
                                                      pSurface);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Creating window surface failed: " + translateVulkanResult(error));
            }
            this.surfaceHandle = pSurface.get();
        }
    }

    @Override
    public void close() {
        // Release resources in fields
        this.debug.close();

        // Release window resources
        this.keyCallback.free();
        glfwDestroyWindow(this.windowHandle);
        glfwTerminate();
    }

    /**
     * Tells the OS to show the window.
     */
    public void show() {
        glfwShowWindow(this.windowHandle);
    }

    /**
     * Queries the window if close has been requested.
     *
     * @return <code>true</code> if window has requested to be closed, <code>false</code>
     *         otherwise
     */
    public boolean shouldClose() {
        return glfwWindowShouldClose(this.windowHandle);
    }
}
