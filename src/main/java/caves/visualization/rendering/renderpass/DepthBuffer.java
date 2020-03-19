package caves.visualization.rendering.renderpass;

import caves.visualization.rendering.GPUImage;
import caves.visualization.rendering.swapchain.SwapChain;
import caves.visualization.window.DeviceContext;

import static org.lwjgl.vulkan.VK10.*;

final class DepthBuffer implements AutoCloseable {
    private final GPUImage depthImage;
    private final GPUImage.View depthImageView;

    public long getImageView() {
        return this.depthImageView.getHandle();
    }

    DepthBuffer(
            final DeviceContext deviceContext,
            final SwapChain swapChain,
            final int format
    ) {

        final var extent = swapChain.getExtent();
        this.depthImage = new GPUImage(deviceContext,
                                       extent.width(),
                                       extent.height(),
                                       format,
                                       VK_IMAGE_TILING_OPTIMAL,
                                       VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                                       VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        this.depthImageView = new GPUImage.View(deviceContext.getDeviceHandle(),
                                                this.depthImage.getHandle(),
                                                format,
                                                VK_IMAGE_ASPECT_DEPTH_BIT);
    }

    /**
     * Finds supported depth image format from the given device context. This format can then be
     * used to initialize the buffer.
     *
     * @param deviceContext device to query for supported image formats
     *
     * @return the depth image format
     */
    static int findDepthFormat(final DeviceContext deviceContext) {
        return deviceContext.findImageFormat(new int[]{
                                                     VK_FORMAT_D32_SFLOAT,
                                                     VK_FORMAT_D32_SFLOAT_S8_UINT,
                                                     VK_FORMAT_D24_UNORM_S8_UINT
                                             },
                                             VK_IMAGE_TILING_OPTIMAL,
                                             VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT)
                            .orElseThrow();
    }

    @Override
    public void close() {
        this.depthImage.close();
        this.depthImageView.close();
    }
}
