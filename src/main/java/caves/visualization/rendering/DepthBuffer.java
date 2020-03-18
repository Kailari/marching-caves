package caves.visualization.rendering;

import caves.visualization.rendering.swapchain.RecreatedWithSwapChain;
import caves.visualization.rendering.swapchain.SwapChain;
import caves.visualization.window.DeviceContext;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class DepthBuffer implements RecreatedWithSwapChain {
    private final DeviceContext deviceContext;
    private final SwapChain swapChain;
    private final CommandPool commandPool;

    private long depthImageView;
    private GPUImage depthImage;
    private int format;

    private boolean cleanedUp;

    public int getImageFormat() {
        return this.format;
    }

    public long getImageView() {
        return this.depthImageView;
    }

    public DepthBuffer(
            final DeviceContext deviceContext,
            final SwapChain swapChain,
            final CommandPool commandPool
    ) {
        this.deviceContext = deviceContext;
        this.swapChain = swapChain;
        this.commandPool = commandPool;

        this.cleanedUp = true;

        recreate();
    }

    private static long createImageView(
            final VkDevice device,
            final GPUImage image,
            final int format,
            final int aspectFlags
    ) {
        try (var stack = stackPush()) {
            final var viewInfo = VkImageViewCreateInfo
                    .callocStack()
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(image.getHandle())
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(format);
            viewInfo.subresourceRange()
                    .aspectMask(aspectFlags)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);

            final var pImageView = stack.mallocLong(1);
            final var result = vkCreateImageView(device, viewInfo, null, pImageView);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create texture image view: "
                                                        + translateVulkanResult(result));
            }

            return pImageView.get(0);
        }
    }

    @Override
    public void recreate() {
        assert this.cleanedUp : "Cannot recreate depth buffer without clearing it first!";

        this.format = findDepthFormat();

        final var extent = this.swapChain.getExtent();
        this.depthImage = new GPUImage(this.deviceContext,
                                       extent.width(),
                                       extent.height(),
                                       this.format,
                                       VK_IMAGE_TILING_OPTIMAL,
                                       VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                                       VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        this.depthImageView = createImageView(this.deviceContext.getDeviceHandle(),
                                              this.depthImage,
                                              this.format,
                                              VK_IMAGE_ASPECT_DEPTH_BIT);

        /*this.depthImage.transitionLayout(this.commandPool,
                                         this.format,
                                         VK_IMAGE_LAYOUT_UNDEFINED,
                                         VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);*/

        this.cleanedUp = false;
    }

    private int findDepthFormat() {
        return this.deviceContext.findImageFormat(new int[]{
                                                          VK_FORMAT_D32_SFLOAT,
                                                          VK_FORMAT_D32_SFLOAT_S8_UINT,
                                                          VK_FORMAT_D24_UNORM_S8_UINT
                                                  },
                                                  VK_IMAGE_TILING_OPTIMAL,
                                                  VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT)
                                 .orElseThrow();
    }

    @Override
    public void cleanup() {
        assert !this.cleanedUp : "Depth buffer already cleaned up!";

        vkDestroyImageView(this.deviceContext.getDeviceHandle(), this.depthImageView, null);
        this.depthImage.close();
        this.cleanedUp = true;
    }
}
