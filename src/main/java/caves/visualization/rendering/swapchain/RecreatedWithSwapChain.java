package caves.visualization.rendering.swapchain;

public interface RecreatedWithSwapChain extends AutoCloseable {
    /**
     * (Re)Creates the resource. Called to either initialize the resources or re-create them after
     * {@link #cleanup()} was called.
     */
    void recreate();

    /**
     * Releases resources in preparations for re-creation or shutdown.
     */
    void cleanup();

    @Override
    default void close() {
        cleanup();
    }
}
