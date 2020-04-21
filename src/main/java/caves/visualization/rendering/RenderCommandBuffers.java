package caves.visualization.rendering;

import caves.visualization.LineVertex;
import caves.visualization.PolygonVertex;
import caves.visualization.rendering.command.CommandBuffer;
import caves.visualization.rendering.command.CommandPool;
import caves.visualization.rendering.mesh.Mesh;
import caves.visualization.rendering.renderpass.RenderPass;
import caves.visualization.rendering.swapchain.GraphicsPipeline;
import caves.visualization.rendering.swapchain.RecreatedWithSwapChain;
import caves.visualization.rendering.swapchain.SwapChain;
import caves.visualization.window.DeviceContext;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;

import javax.annotation.Nullable;
import java.util.Collection;

public final class RenderCommandBuffers implements RecreatedWithSwapChain {
    private final VkDevice device;
    private final CommandPool commandPool;
    private final SwapChain swapChain;
    private final GraphicsPipeline<LineVertex> linePipeline;
    private final GraphicsPipeline<PolygonVertex> polygonPipeline;
    private final RenderPass renderPass;

    @Nullable private Mesh<LineVertex> lineMesh;
    @Nullable private Collection<Mesh<PolygonVertex>> polygonMeshes;

    @SuppressWarnings("NotNullFieldNotInitialized")
    private CommandBuffer[] commandBuffers;
    private boolean cleanedUp;

    /**
     * Creates new render command buffers for rendering the given fixed vertex buffer.
     *
     * @param deviceContext   the device context to use
     * @param commandPool     command pool to allocate on
     * @param swapChain       active swapchain
     * @param linePipeline    the graphics pipeline for rendering meshes as line strips
     * @param polygonPipeline the graphics pipeline for rendering meshes as triangles
     * @param renderPass      the render pass to record commands for
     * @param lineMesh        mesh to render as lines
     * @param polygonMeshes   mesh to render as polygons
     */
    public RenderCommandBuffers(
            final DeviceContext deviceContext,
            final CommandPool commandPool,
            final SwapChain swapChain,
            final GraphicsPipeline<LineVertex> linePipeline,
            final GraphicsPipeline<PolygonVertex> polygonPipeline,
            final RenderPass renderPass,
            @Nullable final Mesh<LineVertex> lineMesh,
            @Nullable final Collection<Mesh<PolygonVertex>> polygonMeshes
    ) {
        this.device = deviceContext.getDeviceHandle();
        this.commandPool = commandPool;
        this.swapChain = swapChain;
        this.linePipeline = linePipeline;
        this.polygonPipeline = polygonPipeline;
        this.renderPass = renderPass;
        this.lineMesh = lineMesh;
        this.polygonMeshes = polygonMeshes;

        this.cleanedUp = true;

        recreate();
    }

    /**
     * Gets the command buffer for framebuffer/image view with given index.
     *
     * @param imageIndex index of the image view or framebuffer to use
     *
     * @return the command buffer
     */
    public VkCommandBuffer getBufferForImage(final int imageIndex) {
        assert !this.cleanedUp : "Tried to fetch buffer from cleaned up command buffers!";
        return this.commandBuffers[imageIndex].getHandle();
    }

    /**
     * Re-creates the command buffers.
     */
    public void recreate() {
        assert this.cleanedUp : "Tried to recreate render command buffers not cleared!";

        final var imageCount = this.swapChain.getImageCount();
        this.commandBuffers = CommandBuffer.allocate(this.device,
                                                     imageCount,
                                                     this.commandPool.getHandle());

        for (var imageIndex = 0; imageIndex < imageCount; ++imageIndex) {
            recordCommands(imageIndex);
        }

        this.cleanedUp = false;
    }

    private void recordCommands(final int imageIndex) {
        this.commandBuffers[imageIndex].begin(() -> {
            try (var ignored = this.renderPass.begin(this.commandBuffers[imageIndex].getHandle(), imageIndex)) {
                if (this.polygonMeshes != null) {
                    this.polygonPipeline.bind(this.commandBuffers[imageIndex], imageIndex);
                    for (final var mesh : this.polygonMeshes) {
                        mesh.draw(this.commandBuffers[imageIndex].getHandle());
                    }
                }

                if (this.lineMesh != null) {
                    this.linePipeline.bind(this.commandBuffers[imageIndex], imageIndex);
                    this.lineMesh.draw(this.commandBuffers[imageIndex].getHandle());
                }
            }
        });
    }

    /**
     * Releases command buffers in preparations for re-creation or shutdown.
     */
    @Override
    public void cleanup() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried to cleanup render command buffers while already cleared!");
        }

        for (final var commandBuffer : this.commandBuffers) {
            commandBuffer.close();
        }

        this.cleanedUp = true;
    }

    /**
     * Updates the rendered meshes.
     *
     * @param caveMeshes polygon mesh
     * @param lineMesh   line mesh
     */
    public void setMeshes(
            @Nullable final Collection<Mesh<PolygonVertex>> caveMeshes,
            @Nullable final Mesh<LineVertex> lineMesh
    ) {
        this.polygonMeshes = caveMeshes;
        this.lineMesh = lineMesh;
    }
}
