package caves.visualization.window.rendering;

import caves.visualization.window.DeviceContext;
import caves.visualization.window.rendering.swapchain.Framebuffers;
import caves.visualization.window.rendering.swapchain.GraphicsPipeline;
import caves.visualization.window.rendering.swapchain.SwapChain;
import caves.visualization.window.rendering.uniform.DescriptorPool;
import caves.visualization.window.rendering.uniform.UniformBufferObject;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.util.Arrays;

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

    private final SequentialGPUBuffer<GraphicsPipeline.Vertex> vertexBuffer;
    private final SequentialGPUBuffer<Short> indexBuffer;
    private final RenderCommandBuffers renderCommandBuffers;
    private final UniformBufferObject uniformBufferObject;
    private final DescriptorPool descriptorPool;

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

            this.renderCommandBuffers.cleanup();
            this.framebuffers.cleanup();
            this.graphicsPipeline.cleanup();
            this.uniformBufferObject.cleanup();
            this.descriptorPool.cleanup();
            this.swapChain.cleanup();

            this.swapChain.recreate();
            this.descriptorPool.recreate();
            this.uniformBufferObject.recreate();
            this.graphicsPipeline.recreate();
            this.framebuffers.recreate();
            this.renderCommandBuffers.recreate();
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
        this.descriptorPool = new DescriptorPool(this.deviceContext, this.swapChain);
        this.uniformBufferObject = new UniformBufferObject(this.deviceContext, this.swapChain, this.descriptorPool);
        this.graphicsPipeline = new GraphicsPipeline(deviceContext.getDevice(),
                                                     this.swapChain,
                                                     this.uniformBufferObject);
        this.framebuffers = new Framebuffers(deviceContext.getDevice(), this.graphicsPipeline, this.swapChain);
        this.commandPool = new CommandPool(deviceContext, this.swapChain);

        final var quadSize = 0.5f;
        final var vertices = new GraphicsPipeline.Vertex[]{
                new GraphicsPipeline.Vertex(new Vector2f(-quadSize, -quadSize), new Vector3f(1.0f, 0.0f, 0.0f)),
                new GraphicsPipeline.Vertex(new Vector2f(quadSize, -quadSize), new Vector3f(0.0f, 1.0f, 0.0f)),
                new GraphicsPipeline.Vertex(new Vector2f(quadSize, quadSize), new Vector3f(0.0f, 0.0f, 1.0f)),
                new GraphicsPipeline.Vertex(new Vector2f(-quadSize, quadSize), new Vector3f(1.0f, 0.0f, 1.0f)),
        };
        final var indices = new Short[]{
                0, 1, 2,
                2, 3, 0,
        };

        this.vertexBuffer = createVertexBuffer(deviceContext, this.commandPool, vertices);
        this.indexBuffer = createIndexBuffer(deviceContext, this.commandPool, indices);

        this.renderCommandBuffers = new RenderCommandBuffers(deviceContext,
                                                             this.commandPool,
                                                             this.swapChain,
                                                             this.framebuffers,
                                                             this.graphicsPipeline,
                                                             this.vertexBuffer,
                                                             this.indexBuffer,
                                                             this.uniformBufferObject);

    }

    private static SequentialGPUBuffer<GraphicsPipeline.Vertex> createVertexBuffer(
            final DeviceContext deviceContext,
            final CommandPool commandPool,
            final GraphicsPipeline.Vertex[] vertices
    ) {
        final var stagingBuffer = new SequentialGPUBuffer<GraphicsPipeline.Vertex>(
                deviceContext,
                vertices.length,
                GraphicsPipeline.Vertex.SIZE_IN_BYTES,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                (buffer, vertex) -> {
                    buffer.putFloat(vertex.getPos().x());
                    buffer.putFloat(vertex.getPos().y());

                    buffer.putFloat(vertex.getColor().x());
                    buffer.putFloat(vertex.getColor().y());
                    buffer.putFloat(vertex.getColor().z());
                });
        final var vertexBuffer = new SequentialGPUBuffer<GraphicsPipeline.Vertex>(
                deviceContext,
                vertices.length,
                GraphicsPipeline.Vertex.SIZE_IN_BYTES,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                null);
        stagingBuffer.pushElements(Arrays.asList(vertices));
        stagingBuffer.copyToAndWait(commandPool.getHandle(),
                                    deviceContext.getGraphicsQueue(),
                                    vertexBuffer);
        stagingBuffer.close();

        return vertexBuffer;
    }

    private static SequentialGPUBuffer<Short> createIndexBuffer(
            final DeviceContext deviceContext,
            final CommandPool commandPool,
            final Short[] indices
    ) {
        final var stagingBuffer = new SequentialGPUBuffer<Short>(
                deviceContext,
                indices.length,
                Short.BYTES,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                ByteBuffer::putShort);
        final var vertexBuffer = new SequentialGPUBuffer<Short>(
                deviceContext,
                indices.length,
                Short.BYTES,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                null);
        stagingBuffer.pushElements(indices);
        stagingBuffer.copyToAndWait(commandPool.getHandle(),
                                    deviceContext.getGraphicsQueue(),
                                    vertexBuffer);
        stagingBuffer.close();

        return vertexBuffer;
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
        this.descriptorPool.close();
        this.framebuffers.close();
        this.graphicsPipeline.close();
        this.swapChain.close();
        this.uniformBufferObject.close();

        this.vertexBuffer.close();
        this.indexBuffer.close();
    }

    /**
     * Notifies the rendering context that the swapchain has been invalidated and should be
     * re-created. Causes the next call to {@link #getSwapChain()} to trigger re-creating the
     * context.
     */
    public void notifyOutOfDateSwapchain() {
        this.mustRecreateSwapChain = true;
    }

    /**
     * Updates shader uniform buffer objects.
     *
     * @param imageIndex index of the current swapchain image
     * @param angle      angle for the model matrix
     */
    public void updateUniforms(final int imageIndex, final float angle) {
        this.uniformBufferObject.update(imageIndex, angle);
    }
}
