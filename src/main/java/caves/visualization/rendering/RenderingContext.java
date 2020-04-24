package caves.visualization.rendering;

import caves.visualization.LineVertex;
import caves.visualization.PolygonVertex;
import caves.visualization.rendering.command.CommandPool;
import caves.visualization.rendering.mesh.Mesh;
import caves.visualization.rendering.renderpass.RenderPass;
import caves.visualization.rendering.swapchain.GraphicsPipeline;
import caves.visualization.rendering.swapchain.SwapChain;
import caves.visualization.rendering.uniform.DescriptorPool;
import caves.visualization.rendering.uniform.UniformBufferObject;
import caves.visualization.window.DeviceContext;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwWaitEvents;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class RenderingContext implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(RenderingContext.class);

    private final DeviceContext deviceContext;
    private final long windowHandle;

    private final SwapChain swapChain;
    private final RenderPass renderPass;
    private final GraphicsPipeline<LineVertex> linePipeline;
    private final GraphicsPipeline<PolygonVertex> polygonPipeline;
    private final CommandPool commandPool;

    private final RenderCommandBuffers renderCommandBuffers;
    private final UniformBufferObject uniformBufferObject;
    private final DescriptorPool descriptorPool;

    private boolean mustRecreateSwapChain;

    /**
     * Gets the count of swapchain images.
     *
     * @return the count
     */
    public int getSwapChainImageCount() {
        return this.swapChain.getImageViews().length;
    }

    /**
     * Gets the swapchain instance. The swapchain state is valid only if {@link #updateSwapChain()}
     * has been called for the current frame.
     *
     * @return the swapchain state
     */
    public SwapChain getSwapChain() {
        return this.swapChain;
    }

    /**
     * Initializes the required context for rendering on the screen.
     *
     * @param deviceContext device context information to use for creating the swapchain
     * @param surface       surface to create the chain for
     * @param windowHandle  handle to the window
     */
    public RenderingContext(final DeviceContext deviceContext,
            final long surface,
            final long windowHandle
    ) {
        this.deviceContext = deviceContext;
        this.windowHandle = windowHandle;

        this.swapChain = new SwapChain(deviceContext, surface, windowHandle);
        this.commandPool = new CommandPool(deviceContext,
                                           deviceContext.getQueueFamilies().getGraphics());

        this.descriptorPool = new DescriptorPool(this.deviceContext, this.swapChain);
        this.uniformBufferObject = new UniformBufferObject(this.deviceContext, this.swapChain, this.descriptorPool);

        this.renderPass = new RenderPass(this.deviceContext, this.swapChain);

        this.linePipeline = new GraphicsPipeline<>(deviceContext.getDeviceHandle(),
                                                   this.swapChain,
                                                   this.renderPass,
                                                   this.uniformBufferObject,
                                                   "lines",
                                                   "shader",
                                                   LineVertex.FORMAT,
                                                   VK_PRIMITIVE_TOPOLOGY_LINE_STRIP);

        this.polygonPipeline = new GraphicsPipeline<>(deviceContext.getDeviceHandle(),
                                                      this.swapChain,
                                                      this.renderPass,
                                                      this.uniformBufferObject,
                                                      "polygons",
                                                      "polygons",
                                                      PolygonVertex.FORMAT,
                                                      VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

        this.renderCommandBuffers = new RenderCommandBuffers(deviceContext,
                                                             this.commandPool,
                                                             this.swapChain,
                                                             this.linePipeline,
                                                             this.polygonPipeline,
                                                             this.renderPass);
    }

    /**
     * Re-creates the swapchain it in case the current instance has been invalidated. The swapchain
     * is most commonly invalidated on resize, which occurs after GLFW events have been polled.
     * Thus, the intended usage is to call this immediately after <code>glfwPollEvents</code> is
     * called, like this:
     * <pre><code>
     *     glfwPollEvents();
     *     context.updateSwapChain();
     * </code></pre>
     */
    public void updateSwapChain() {
        if (!this.mustRecreateSwapChain) {
            return;
        }

        // Handle minimize
        try (var stack = stackPush()) {
            final var pWidth = stack.mallocInt(1);
            final var pHeight = stack.mallocInt(1);
            glfwGetFramebufferSize(this.windowHandle, pWidth, pHeight);
            while (pWidth.get(0) == 0 && pHeight.get(0) == 0) {
                glfwGetFramebufferSize(this.windowHandle, pWidth, pHeight);
                glfwWaitEvents();
            }
        }

        vkDeviceWaitIdle(this.deviceContext.getDeviceHandle());
        LOG.debug("Re-creating the swapchain!");

        this.renderCommandBuffers.cleanup();

        this.linePipeline.cleanup();
        this.polygonPipeline.cleanup();

        this.uniformBufferObject.cleanup();
        this.descriptorPool.cleanup();
        this.renderPass.cleanup();
        this.swapChain.cleanup();


        this.swapChain.recreate();
        this.descriptorPool.recreate();
        this.uniformBufferObject.recreate();

        this.renderPass.recreate();

        this.linePipeline.recreate();
        this.polygonPipeline.recreate();

        this.renderCommandBuffers.recreate();
        this.mustRecreateSwapChain = false;
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

        this.linePipeline.close();
        this.polygonPipeline.close();

        this.renderPass.close();
        this.swapChain.close();
        this.uniformBufferObject.close();
    }

    /**
     * Notifies the rendering context that the swapchain has been invalidated and should be
     * re-created. Causes the next call to {@link #updateSwapChain()} to trigger re-creating the
     * context.
     */
    public void notifyOutOfDateSwapchain() {
        this.mustRecreateSwapChain = true;
    }

    /**
     * Updates shader uniform buffer objects.
     *
     * @param imageIndex     index of the current swapchain image
     * @param angle          angle for the model matrix
     * @param lookAtDistance how far from the origin the camera is placed
     */
    public void updateUniforms(
            final int imageIndex,
            final float angle,
            final float lookAtDistance
    ) {
        this.uniformBufferObject.update(imageIndex, angle, lookAtDistance);
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
        this.renderCommandBuffers.cleanup();
        this.renderCommandBuffers.setMeshes(caveMeshes, lineMesh);
        this.renderCommandBuffers.recreate();
    }
}
