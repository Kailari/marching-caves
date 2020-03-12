package caves.window;

import caves.window.rendering.RenderingContext;
import org.lwjgl.PointerBuffer;

import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported;
import static org.lwjgl.system.MemoryUtil.memAllocPointer;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;

/**
 * Wrapper over window and rendering context information. Handles GLFW window and vulkan instance
 * initialization. Sets up rendering pipeline etc.
 */
public final class ApplicationContext implements AutoCloseable {
    private final VulkanInstance instance;
    private final DeviceContext deviceContext;
    private final RenderingContext renderContext;

    private final GLFWVulkanWindow window;

    /**
     * Gets the application window.
     *
     * @return the app window
     */
    public GLFWVulkanWindow getWindow() {
        return this.window;
    }

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
    public ApplicationContext(final int width, final int height, final boolean enableValidation) {
        if (!glfwInit()) {
            throw new IllegalStateException("Initializing GLFW failed.");
        }
        if (!glfwVulkanSupported()) {
            throw new IllegalStateException("GLFW could not find Vulkan loader.");
        }

        this.instance = new VulkanInstance(getRequiredExtensions(),
                                           getValidationLayers(),
                                           enableValidation);
        this.window = new GLFWVulkanWindow(width, height, this.instance);
        this.deviceContext = DeviceContext.getForInstance(this.instance,
                                                          this.window.getSurfaceHandle(),
                                                          getRequiredDeviceExtensions());
        this.renderContext = new RenderingContext(this.deviceContext,
                                                  this.window.getSurfaceHandle(),
                                                  this.window.getHandle());
    }

    @Override
    public void close() {
        // Release resources
        this.renderContext.close();
        this.deviceContext.close();
        this.window.destroySurface(this.instance);

        // Destroy the instance last as performing this renders the rest of the resources invalid
        this.instance.close();

        // Release window resources
        this.window.close();
    }
}
