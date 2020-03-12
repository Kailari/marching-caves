package caves.window;

import org.lwjgl.glfw.GLFWKeyCallback;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public final class GLFWVulkanWindow implements AutoCloseable {
    private final int width;
    private final int height;
    private final long windowHandle;
    private final GLFWKeyCallback keyCallback;
    private final long surfaceHandle;

    /**
     * Returns the width of this window's surface in pixels.
     *
     * @return the window width
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * Returns the height of this window's surface in pixels.
     *
     * @return the window height
     */
    public int getHeight() {
        return this.height;
    }

    /**
     * Gets a handle for the rendering surface.
     *
     * @return the surface handle
     */
    public long getSurfaceHandle() {
        return this.surfaceHandle;
    }

    /**
     * Initializes a new GLFW window with given size and allocates a vulkan compatible rendering
     * surface for it.
     *
     * @param width    window width
     * @param height   window height
     * @param instance the vulkan instance
     */
    public GLFWVulkanWindow(final int width, final int height, final VulkanInstance instance) {
        this.width = width;
        this.height = height;

        try (var stack = stackPush()) {
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
                        glfwSetWindowShouldClose(GLFWVulkanWindow.this.windowHandle, true);
                    }
                }
            };
            glfwSetKeyCallback(this.windowHandle, this.keyCallback);
            final var pSurface = stack.mallocLong(1);
            final var error = glfwCreateWindowSurface(instance.getInstance(),
                                                      this.windowHandle,
                                                      null,
                                                      pSurface);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Creating window surface failed: " + translateVulkanResult(error));
            }
            this.surfaceHandle = pSurface.get(0);
        }
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

    /**
     * Destroys the associated surface. After this, the window is practically useless and should be
     * {@link #close()} closed. This should only be called during shutdown procedures.
     *
     * @param instance the instance the surface was created on
     */
    public void destroySurface(final VulkanInstance instance) {
        vkDestroySurfaceKHR(instance.getInstance(), this.surfaceHandle, null);
    }

    /**
     * NOTE: Does not release the window surface. {@link #destroySurface(VulkanInstance)} must be
     * called before the instance is destroyed!
     */
    @Override
    public void close() {
        this.keyCallback.free();
        glfwDestroyWindow(this.windowHandle);
        glfwTerminate();
    }
}
