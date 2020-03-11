package caves.window.rendering;

import caves.window.DeviceContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.List;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public final class SwapChain implements AutoCloseable {
    private final DeviceContext deviceContext;
    private final long surface;

    private long swapchain;
    private long[] images = new long[0];
    private long[] imageViews = new long[0];
    private VkExtent2D extent;
    private int imageFormat;

    /**
     * Gets the image extent of this swapchain.
     *
     * @return the extent
     */
    public VkExtent2D getExtent() {
        return this.extent;
    }

    /**
     * Gets the image format in use on this swapchain.
     *
     * @return the active image format
     */
    public int getImageFormat() {
        return this.imageFormat;
    }

    /**
     * Creates a new swapchain for the given device.
     *
     * @param deviceContext device context information to use for creating the swapchain
     * @param surface       surface to create the chain for
     * @param windowWidth   desired window surface width
     * @param windowHeight  desired window surface height
     */
    public SwapChain(
            final DeviceContext deviceContext,
            final long surface,
            final int windowWidth,
            final int windowHeight
    ) {
        this.deviceContext = deviceContext;
        this.surface = surface;
        this.swapchain = VK_NULL_HANDLE;

        this.recreate(windowWidth, windowHeight);
    }

    private static VkSwapchainCreateInfoKHR createSwapChainCreateInfo(
            final MemoryStack stack, final DeviceContext deviceContext,
            final long surface,
            final int windowWidth,
            final int windowHeight
    ) {
        final var swapChainSupport = SwapChainSupportDetails.querySupport(stack,
                                                                          deviceContext.getPhysicalDevice(),
                                                                          surface);

        final var surfaceFormat = chooseSurfaceFormat(swapChainSupport.getSurfaceFormats());
        final var presentMode = choosePresentMode(swapChainSupport.getPresentModes());
        final var extent = chooseExtent(stack, swapChainSupport.getSurfaceCapabilities(), windowWidth, windowHeight);
        final var imageCount = chooseImageCount(swapChainSupport);

        final var createInfo = VkSwapchainCreateInfoKHR
                .callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                .surface(surface)
                .minImageCount(imageCount)
                .imageFormat(surfaceFormat.format())
                .imageColorSpace(surfaceFormat.colorSpace())
                .imageExtent(extent)
                .imageArrayLayers(1)
                .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .preTransform(swapChainSupport.getSurfaceCapabilities().currentTransform())
                .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .presentMode(presentMode)
                .clipped(true)
                .oldSwapchain(VK_NULL_HANDLE);

        final var graphicsFamily = deviceContext.getGraphicsQueueFamilyIndex();
        final var presentationFamily = deviceContext.getPresentationQueueFamilyIndex();
        if (graphicsFamily != presentationFamily) {
            final var queueFamilyIndices = stack.mallocInt(2);
            queueFamilyIndices.put(graphicsFamily);
            queueFamilyIndices.put(presentationFamily);
            queueFamilyIndices.flip();

            createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT);
            createInfo.pQueueFamilyIndices(queueFamilyIndices);
        } else {
            createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
        }
        return createInfo;
    }

    private static VkSurfaceFormatKHR chooseSurfaceFormat(final List<VkSurfaceFormatKHR> availableFormats) {
        for (var format : availableFormats) {
            final var hasDesiredFormat = format.format() == VK_FORMAT_B8G8R8_SRGB;
            final var hasDesiredColorSpace = format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
            if (hasDesiredFormat && hasDesiredColorSpace) {
                return format;
            }
        }

        // Fallback to first available option
        return availableFormats.get(0);
    }

    private static int choosePresentMode(final List<Integer> availablePresentModes) {
        // Use "triple buffering", if available
        for (var presentMode : availablePresentModes) {
            if (presentMode == VK_PRESENT_MODE_MAILBOX_KHR) {
                return presentMode;
            }
        }

        // Fallback to FIFO (guaranteed to be available by the specification)
        return VK_PRESENT_MODE_FIFO_KHR;
    }

    private static VkExtent2D chooseExtent(
            final MemoryStack stack,
            final VkSurfaceCapabilitiesKHR capabilities,
            final int windowWidth,
            final int windowHeight
    ) {
        // By the spec, window managers can set the width/height to uint32_t max value to indicate
        // that there are constraints on the extent min/max size.
        if (capabilities.currentExtent().width() != 0xFFFFFFFF /* uint32_t MAX */) {
            return capabilities.currentExtent();
        }

        return VkExtent2D.callocStack(stack)
                         .width(Math.min(capabilities.minImageExtent().width(),
                                         Math.max(capabilities.maxImageExtent().width(),
                                                  windowWidth)))
                         .height(Math.min(capabilities.minImageExtent().height(),
                                          Math.max(capabilities.maxImageExtent().height(),
                                                   windowHeight)));
    }

    private static int chooseImageCount(final SwapChainSupportDetails swapChainSupport) {
        final var min = swapChainSupport.getSurfaceCapabilities().minImageCount();
        final var max = swapChainSupport.getSurfaceCapabilities().maxImageCount();

        final var goal = min + 1;
        final var maximumIsDefined = max > 0;
        return (maximumIsDefined && goal > max)
                ? max
                : goal;
    }

    /**
     * (Re)Creates the swapchain. Should only be called after the swapchain is known to be
     * invalidated.
     *
     * @param windowWidth  desired window surface width
     * @param windowHeight desired window surface height
     */
    public void recreate(
            final int windowWidth,
            final int windowHeight
    ) {
        try (var stack = stackPush()) {
            final var createInfo = createSwapChainCreateInfo(stack,
                                                             this.deviceContext,
                                                             this.surface,
                                                             windowWidth, windowHeight);

            final var pSwapChain = stack.mallocLong(1);
            final var error = vkCreateSwapchainKHR(this.deviceContext.getDevice(), createInfo, null, pSwapChain);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Creating swapchain failed: "
                                                        + translateVulkanResult(error));
            }

            // Clean up the old swapchain (if present) and replace the handle and properties
            cleanup();
            this.swapchain = pSwapChain.get(0);
            this.imageFormat = createInfo.imageFormat();
            this.extent = createInfo.imageExtent();
        }

        try (var stack = stackPush()) {
            final var imageCount = stack.mallocInt(1);
            var error = vkGetSwapchainImagesKHR(this.deviceContext.getDevice(),
                                                this.swapchain,
                                                imageCount,
                                                null);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Could not get swapchain image count: "
                                                        + translateVulkanResult(error));
            }

            final var pSwapchainImages = stack.mallocLong(imageCount.get(0));
            error = vkGetSwapchainImagesKHR(this.deviceContext.getDevice(),
                                            this.swapchain,
                                            imageCount,
                                            pSwapchainImages);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Could not get swapchain images: "
                                                        + translateVulkanResult(error));
            }

            this.images = new long[imageCount.get(0)];
            this.imageViews = new long[imageCount.get(0)];
            pSwapchainImages.get(this.images);

            for (var i = 0; i < imageCount.get(0); ++i) {
                final var imgvCreateInfo = VkImageViewCreateInfo
                        .callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                        .image(this.images[i])
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(this.imageFormat);
                imgvCreateInfo.components()
                              .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                              .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                              .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                              .a(VK_COMPONENT_SWIZZLE_IDENTITY);
                imgvCreateInfo.subresourceRange()
                              .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                              .baseMipLevel(0)
                              .levelCount(1)
                              .baseArrayLayer(0)
                              .layerCount(1);

                final var pImageView = stack.mallocLong(1);
                error = vkCreateImageView(this.deviceContext.getDevice(),
                                          imgvCreateInfo,
                                          null,
                                          pImageView);
                if (error != VK_SUCCESS) {
                    throw new IllegalStateException("Creating image view failed: "
                                                            + translateVulkanResult(error));
                }
                this.imageViews[i] = pImageView.get(0);
            }
        }
    }

    @Override
    public void close() {
        cleanup();
    }

    private void cleanup() {
        if (this.swapchain != VK_NULL_HANDLE) {
            cleanupImageViews();
            vkDestroySwapchainKHR(this.deviceContext.getDevice(), this.swapchain, null);
        }
    }

    private void cleanupImageViews() {
        for (var imageView : this.imageViews) {
            vkDestroyImageView(this.deviceContext.getDevice(), imageView, null);
        }
    }
}
