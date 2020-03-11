package caves.window.rendering;

import caves.window.DeviceContext;
import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;

public final class RenderingContext implements AutoCloseable {
    private final DeviceContext deviceContext;

    private final SwapChain swapChain;
    private final GraphicsPipeline graphicsPipeline;
    private final Framebuffers framebuffers;
    private final CommandBuffers commandBuffers;
    private boolean mustRecreateSwapChain = false;

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
        this.deviceContext = deviceContext;

        this.swapChain = new SwapChain(deviceContext, surface, windowWidth, windowHeight);
        this.graphicsPipeline = new GraphicsPipeline(deviceContext.getDevice(), this.swapChain);
        this.framebuffers = new Framebuffers(deviceContext.getDevice(), this.graphicsPipeline, this.swapChain);
        this.commandBuffers = new CommandBuffers(deviceContext, this.swapChain, this.framebuffers, this.graphicsPipeline);
    }

    public VkCommandBuffer getCommandBufferForImage(final int imageIndex) {
        return this.commandBuffers.getBufferForImage(imageIndex);
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
     * @param windowWidth  mostly ignored, in case of swapchain re-creation, used to enforce the new
     *                     window surface width
     * @param windowHeight mostly ignored, in case of swapchain re-creation, used to enforce the new
     *                     window surface height
     *
     * @return the current swapchain instance, which is guaranteed to be valid
     */
    public SwapChain getSwapChain(final int windowWidth, final int windowHeight) {
        if (this.mustRecreateSwapChain) {
            vkDeviceWaitIdle(this.deviceContext.getDevice());

            this.swapChain.recreate(windowWidth, windowHeight);
            //this.graphicsPipeline.recreate();
            //this.frameBuffers.recreate();
            //this.commandBuffers.recreate();
            this.mustRecreateSwapChain = false;
        }

        return this.swapChain;
    }

    @Override
    public void close() {
        this.commandBuffers.close();
        this.framebuffers.close();
        this.graphicsPipeline.close();
        this.swapChain.close();
    }
}
