package caves.window.rendering;

import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;

public class SwapChainSupportDetails implements AutoCloseable {
    private final VkSurfaceCapabilitiesKHR surfaceCapabilities;
    private final VkSurfaceFormatKHR[] surfaceFormats;

    public SwapChainSupportDetails(
            final VkSurfaceCapabilitiesKHR surfaceCapabilities,
            final VkSurfaceFormatKHR[] surfaceFormats
    ) {
        this.surfaceCapabilities = surfaceCapabilities;
        this.surfaceFormats = surfaceFormats;
    }

    @Override
    public void close() throws Exception {
        this.surfaceCapabilities.close();
    }
}
