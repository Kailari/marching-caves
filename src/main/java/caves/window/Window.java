package caves.window;

import caves.window.rendering.RenderingContext;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWKeyCallback;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

/**
 * Wrapper over GLFW window context. Handles vulkan instance initialization.
 */
public final class Window implements AutoCloseable {
    private final VulkanInstance instance;
    private final DeviceContext deviceContext;

    private final RenderingContext renderContext;

    private final long windowHandle;
    private final GLFWKeyCallback keyCallback;
    private final long surfaceHandle;

    /**
     * Gets the required rendering context for issuing rendering commands.
     *
     * @return rendering context wrapper
     */
    public RenderingContext getRenderContext() {
        return this.renderContext;
    }

    /**
     * Gets the device context required for interfacing with the physical and the logical devices.
     *
     * @return device context wrapper containing the currently active physical and logical devices
     */
    public DeviceContext getDeviceContext() {
        return this.deviceContext;
    }

    private PointerBuffer getRequiredExtensions() {
        final var requiredExtensions = glfwGetRequiredInstanceExtensions();
        if (requiredExtensions == null) {
            throw new IllegalStateException("Failed to find list of required Vulkan extensions!");
        }
        return requiredExtensions;
    }

    private static PointerBuffer getRequiredDeviceExtensions() {
        final var requiredExtensions = memAllocPointer(1);
        requiredExtensions.put(memUTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME));
        requiredExtensions.flip();

        return requiredExtensions;
    }

    private static PointerBuffer getValidationLayers() {
        final var validationLayers = memAllocPointer(1);
        validationLayers.put(memUTF8("VK_LAYER_LUNARG_standard_validation"));
        validationLayers.flip();

        return validationLayers;
    }

    /**
     * Initializes a GLFW window with a vulkan context.
     *
     * @param width            initial width of the window
     * @param height           initial height of the window
     * @param enableValidation should the validation/debug features be enabled
     */
    public Window(final int width, final int height, final boolean enableValidation) {
        if (!glfwInit()) {
            throw new IllegalStateException("Initializing GLFW failed.");
        }
        if (!glfwVulkanSupported()) {
            throw new IllegalStateException("GLFW could not find Vulkan loader.");
        }

        try (var stack = stackPush()) {
            // Initialize vulkan instance
            this.instance = new VulkanInstance(getRequiredExtensions(),
                                               getValidationLayers(),
                                               enableValidation);

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
            final var error = glfwCreateWindowSurface(this.instance.getInstance(),
                                                      this.windowHandle,
                                                      null,
                                                      pSurface);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Creating window surface failed: " + translateVulkanResult(error));
            }
            this.surfaceHandle = pSurface.get(0);

            // Initialize physical and logical device context
            this.deviceContext = DeviceContext.getForInstance(this.instance,
                                                              this.surfaceHandle,
                                                              getRequiredDeviceExtensions());

            // Initialize render context
            this.renderContext = new RenderingContext(this.deviceContext,
                                                      this.surfaceHandle,
                                                      width, height);
        }
    }

    @Override
    public void close() {
        // Release resources
        this.renderContext.close();
        this.deviceContext.close();
        vkDestroySurfaceKHR(this.instance.getInstance(), this.surfaceHandle, null);

        // Destroy the instance last as performing this renders the rest of the resources invalid
        this.instance.close();

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
