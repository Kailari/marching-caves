package caves.window.rendering;

import caves.window.DeviceContext;

public final class RenderingContext implements AutoCloseable {
    private final SwapChain swapChain;

    private boolean mustRecreateSwapChain;

    /**
     * Gets the swapchain and re-creates it in case the current instance has been invalidated. The
     * result is considered invalid after GLFW events have been polled. Thus, the intended usage is
     * to call get the swapchain once, immediately after polling the events:
     * <pre><code>
     *     glfwPollEvents();
     *     final var swapchain = context.getSwapChain();
     * </code></pre>
     *
     * @return the current swapchain instance, which is guaranteed to be valid
     */
    public SwapChain getSwapChain() {
        if (this.mustRecreateSwapChain) {
            //this.swapChain.recreate();
            this.mustRecreateSwapChain = false;
        }

        return this.swapChain;
    }

    /**
     * Initializes the required context for rendering on the screen.
     *
     * @param deviceContext device context information to use for creating the swapchain
     * @param surface       surface to create the chain for
     * @param windowWidth   desired window surface width
     * @param windowHeight  desired window surface height
     */
    public RenderingContext(
            final DeviceContext deviceContext,
            final long surface,
            final int windowWidth,
            final int windowHeight
    ) {
        this.swapChain = new SwapChain(deviceContext, surface, windowWidth, windowHeight);
    }

    @Override
    public void close() {
        this.swapChain.close();
    }
}
