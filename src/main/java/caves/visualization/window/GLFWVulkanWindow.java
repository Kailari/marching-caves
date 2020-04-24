package caves.visualization.window;

import org.lwjgl.glfw.GLFWKeyCallback;

import java.util.ArrayList;
import java.util.Collection;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public final class GLFWVulkanWindow implements AutoCloseable {
    private final long windowHandle;
    private final GLFWKeyCallback keyCallback;
    private final long surfaceHandle;

    private final Collection<ResizeCallback> resizeCallbacks = new ArrayList<>(1);

    /**
     * Gets a handle for the rendering surface.
     *
     * @return the surface handle
     */
    public long getSurfaceHandle() {
        return this.surfaceHandle;
    }

    /**
     * Gets the raw window handle.
     *
     * @return the window handle
     */
    public long getHandle() {
        return this.windowHandle;
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
        try (var stack = stackPush()) {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
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

            glfwSetFramebufferSizeCallback(this.windowHandle,
                                           (window, w, h) ->
                                                   this.resizeCallbacks.forEach(cb -> cb.resize(window, w, h)));
        }
    }

    /**
     * Registers the resize callback to be run when the window is resized.
     *
     * @param resizeCallback the callback
     */
    public void onResize(final ResizeCallback resizeCallback) {
        this.resizeCallbacks.add(resizeCallback);
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

    public interface ResizeCallback {
        /**
         * Called when window is resized.
         *
         * @param windowHandle window handle
         * @param width        new width
         * @param height       new height
         */
        void resize(long windowHandle, int width, int height);
    }
}
