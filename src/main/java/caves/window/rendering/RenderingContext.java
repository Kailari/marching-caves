package caves.window.rendering;

import caves.window.DeviceContext;
import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwWaitEvents;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;

public final class RenderingContext implements AutoCloseable {
    private final DeviceContext deviceContext;
    private final long windowHandle;

    private final SwapChain swapChain;
    private final GraphicsPipeline graphicsPipeline;
    private final Framebuffers framebuffers;
    private final CommandBuffers commandBuffers;
    private boolean mustRecreateSwapChain = false;

    /**
     * Gets the count of swapchain images.
     *
     * @return the count
     */
    public int getSwapChainImageCount() {
        return this.swapChain.getImageViews().length;
    }

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
            System.out.println("Re-creating swapchain!");
            try (var stack = stackPush()) {
                final var pWidth = stack.mallocInt(1);
                final var pHeight = stack.mallocInt(1);
                glfwGetFramebufferSize(this.windowHandle, pWidth, pHeight);
                while (pWidth.get(0) == 0 && pHeight.get(0) == 0) {
                    glfwGetFramebufferSize(this.windowHandle, pWidth, pHeight);
                    glfwWaitEvents();
                }
            }

            vkDeviceWaitIdle(this.deviceContext.getDevice());

            this.framebuffers.cleanup();
            this.commandBuffers.cleanup();
            this.graphicsPipeline.cleanup();
            this.swapChain.cleanup();

            this.swapChain.recreate();
            this.graphicsPipeline.recreate(this.swapChain);
            this.framebuffers.recreate(this.graphicsPipeline, this.swapChain);
            this.commandBuffers.recreate(this.swapChain, this.framebuffers, this.graphicsPipeline);
            this.mustRecreateSwapChain = false;
        }

        return this.swapChain;
    }

    /**
     * Initializes the required context for rendering on the screen.
     *
     * @param deviceContext device context information to use for creating the swapchain
     * @param surface       surface to create the chain for
     * @param windowHandle  handle to the window
     */
    public RenderingContext(
            final DeviceContext deviceContext,
            final long surface,
            final long windowHandle
    ) {
        this.deviceContext = deviceContext;
        this.windowHandle = windowHandle;

        this.swapChain = new SwapChain(deviceContext, surface, windowHandle);
        this.graphicsPipeline = new GraphicsPipeline(deviceContext.getDevice(), this.swapChain);
        this.framebuffers = new Framebuffers(deviceContext.getDevice(), this.graphicsPipeline, this.swapChain);
        this.commandBuffers = new CommandBuffers(deviceContext, this.swapChain, this.framebuffers, this.graphicsPipeline);
    }

    /**
     * Gets the command buffer for given image index. The indices match those of the framebuffers
     * and the swapchain image views.
     *
     * @param imageIndex index of the image view or the framebuffer
     *
     * @return the command buffer
     */
    public VkCommandBuffer getCommandBufferForImage(final int imageIndex) {
        return this.commandBuffers.getBufferForImage(imageIndex);
    }

    @Override
    public void close() {
        this.commandBuffers.close();
        this.framebuffers.close();
        this.graphicsPipeline.close();
        this.swapChain.close();
    }

    /**
     * Notifies the rendering context that the swapchain has been invalidated and should be
     * re-created. Causes the next call to {@link #getSwapChain()} to trigger re-creating the
     * context.
     */
    public void notifyOutOfDateSwapchain() {
        this.mustRecreateSwapChain = true;
    }
}
