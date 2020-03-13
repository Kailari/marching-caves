package caves.visualization.window.rendering;

import caves.visualization.window.DeviceContext;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwWaitEvents;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class RenderingContext implements AutoCloseable {
    private final DeviceContext deviceContext;
    private final long windowHandle;

    private final SwapChain swapChain;
    private final GraphicsPipeline graphicsPipeline;
    private final Framebuffers framebuffers;
    private final CommandPool commandPool;

    private final VertexBuffer vertexBuffer;
    private final RenderCommandBuffers renderCommandBuffers;

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
            this.renderCommandBuffers.cleanup();
            this.graphicsPipeline.cleanup();
            this.swapChain.cleanup();

            this.swapChain.recreate();
            this.graphicsPipeline.recreate(this.swapChain);
            this.framebuffers.recreate(this.graphicsPipeline, this.swapChain);
            this.renderCommandBuffers.recreate(this.swapChain, this.framebuffers, this.graphicsPipeline);
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
        this.commandPool = new CommandPool(deviceContext);

        final var vertices = new GraphicsPipeline.Vertex[]{
                new GraphicsPipeline.Vertex(new Vector2f(0.0f, -0.5f), new Vector3f(1.0f, 0.0f, 0.0f)),
                new GraphicsPipeline.Vertex(new Vector2f(0.5f, 0.5f), new Vector3f(0.0f, 1.0f, 0.0f)),
                new GraphicsPipeline.Vertex(new Vector2f(-0.5f, 0.5f), new Vector3f(0.0f, 0.0f, 1.0f))
        };

        final VertexBuffer stagingBuffer = new VertexBuffer(deviceContext,
                                                            vertices.length,
                                                            VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                                                            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        this.vertexBuffer = new VertexBuffer(deviceContext,
                                             vertices.length,
                                             VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                                             VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        stagingBuffer.pushVertices(vertices);
        stagingBuffer.copyToAndWait(this.commandPool.getHandle(),
                                    this.deviceContext.getGraphicsQueue(),
                                    this.vertexBuffer);
        stagingBuffer.close();

        this.renderCommandBuffers = new RenderCommandBuffers(deviceContext,
                                                             this.commandPool,
                                                             this.swapChain,
                                                             this.framebuffers,
                                                             this.graphicsPipeline,
                                                             this.vertexBuffer);

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
        return this.renderCommandBuffers.getBufferForImage(imageIndex);
    }

    @Override
    public void close() {
        this.renderCommandBuffers.close();
        this.commandPool.close();
        this.framebuffers.close();
        this.graphicsPipeline.close();
        this.swapChain.close();

        this.vertexBuffer.close();
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
