package caves.visualization.window;

import caves.visualization.LineVertex;
import caves.visualization.PolygonVertex;
import caves.visualization.rendering.RenderingContext;
import caves.visualization.rendering.mesh.Mesh;
import org.lwjgl.PointerBuffer;

import javax.annotation.Nullable;

import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported;
import static org.lwjgl.system.MemoryStack.stackPush;
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

    /**
     * Initializes a GLFW window with a vulkan context.
     *
     * @param width            initial width of the window
     * @param height           initial height of the window
     * @param enableValidation should the validation/debug features be enabled
     */
    public ApplicationContext(
            final int width,
            final int height,
            final boolean enableValidation
    ) {
        if (!glfwInit()) {
            throw new IllegalStateException("Initializing GLFW failed.");
        }
        if (!glfwVulkanSupported()) {
            throw new IllegalStateException("GLFW could not find Vulkan loader.");
        }

        try (var stack = stackPush()) {
            this.instance = new VulkanInstance(getRequiredExtensions(),
                                               stack.pointers(stack.UTF8("VK_LAYER_LUNARG_standard_validation")),
                                               enableValidation);
            this.window = new GLFWVulkanWindow(width, height, this.instance);

            this.deviceContext = new DeviceContext(this.instance,
                                                   this.window.getSurfaceHandle(),
                                                   stack.pointers(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME)));
            this.renderContext = new RenderingContext(this.deviceContext,
                                                      this.window.getSurfaceHandle(),
                                                      this.window.getHandle());
        }
    }

    @Override
    public void close() {
        // Release rendering resources
        this.renderContext.close();
        this.deviceContext.close();
        this.window.destroySurface(this.instance);

        // Destroy the instance last as performing this renders the rest of the resources invalid
        this.instance.close();

        // Release window resources
        this.window.close();
    }

    /**
     * Updates the rendered meshes.
     *
     * @param caveMeshes polygon mesh
     * @param lineMesh   line mesh
     */
    public void setMeshes(
            @Nullable final Iterable<Mesh<PolygonVertex>> caveMeshes,
            @Nullable final Mesh<LineVertex> lineMesh
    ) {
        this.renderContext.setMeshes(caveMeshes, lineMesh);
    }
}
