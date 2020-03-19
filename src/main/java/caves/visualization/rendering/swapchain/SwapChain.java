package caves.visualization.rendering.swapchain;

import caves.visualization.rendering.GPUImage;
import caves.visualization.window.DeviceContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

import java.util.Arrays;
import java.util.List;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public final class SwapChain implements RecreatedWithSwapChain {
    private final DeviceContext deviceContext;
    private final VkExtent2D extent;
    private final long surface;
    private final long windowHandle;

    private long swapchain;
    private GPUImage.View[] imageViews;
    private int imageFormat;
    private boolean cleanedUp;

    /**
     * Returns the swapchain handle.
     *
     * @return the swapchain handle
     */
    public long getHandle() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried fetch handle for cleaned up swapchain!");
        }
        return this.swapchain;
    }

    /**
     * Gets the image extent of this swapchain.
     *
     * @return the extent
     */
    public VkExtent2D getExtent() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried fetch extent from cleaned up swapchain!");
        }
        return this.extent;
    }

    /**
     * Gets the image format in use on this swapchain.
     *
     * @return the active image format
     */
    public int getImageFormat() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried fetch image format from cleaned up swapchain!");
        }
        return this.imageFormat;
    }

    /**
     * Gets image views for swapchain images.
     *
     * @return the swapchain image views
     */
    public Long[] getImageViews() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried fetch image views from cleaned up swapchain!");
        }
        return Arrays.stream(this.imageViews)
                     .map(GPUImage.View::getHandle)
                     .toArray(Long[]::new);
    }

    /**
     * Gets the number of images on this swapchain.
     *
     * @return the image count
     */
    public int getImageCount() {
        return this.imageViews.length;
    }

    /**
     * Creates a new swapchain for the given device.
     *
     * @param deviceContext device context information to use for creating the swapchain
     * @param surface       surface to create the chain for
     * @param windowHandle  handle for the GLFW window. Used for fetching window size.
     */
    public SwapChain(
            final DeviceContext deviceContext,
            final long surface,
            final long windowHandle
    ) {
        this.deviceContext = deviceContext;
        this.surface = surface;
        this.windowHandle = windowHandle;
        this.swapchain = VK_NULL_HANDLE;
        this.extent = VkExtent2D.malloc();
        this.cleanedUp = true;

        recreate();
    }

    private static VkSwapchainCreateInfoKHR createSwapChainCreateInfo(
            final MemoryStack stack,
            final DeviceContext deviceContext,
            final long surface,
            final long windowHandle
    ) {
        final var swapChainSupport = SwapChainSupportDetails.querySupport(deviceContext.getPhysicalDevice(),
                                                                          surface);

        final var surfaceFormat = chooseSurfaceFormat(swapChainSupport.getSurfaceFormats());
        final var presentMode = choosePresentMode(swapChainSupport.getPresentModes());
        final var extent = chooseExtent(stack, windowHandle, swapChainSupport.getSurfaceCapabilities());
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

        final var graphicsFamily = deviceContext.getQueueFamilies().getGraphics();
        final var presentationFamily = deviceContext.getQueueFamilies().getPresent();
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
        for (final var format : availableFormats) {
            final var hasDesiredFormat = format.format() == VK_FORMAT_B8G8R8_SRGB;
            final var hasDesiredColorSpace = format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
            if (hasDesiredFormat && hasDesiredColorSpace) {
                return format;
            }
        }

        // Fallback to first available option
        return availableFormats.get(0);
    }

    private static int choosePresentMode(final Iterable<Integer> availablePresentModes) {
        // Use "triple buffering", if available
        for (final var presentMode : availablePresentModes) {
            if (presentMode == VK_PRESENT_MODE_MAILBOX_KHR) {
                return presentMode;
            }
        }

        // Fallback to FIFO (guaranteed to be available by the specification)
        return VK_PRESENT_MODE_FIFO_KHR;
    }

    private static VkExtent2D chooseExtent(
            final MemoryStack stack,
            final long windowHandle,
            final VkSurfaceCapabilitiesKHR capabilities
    ) {
        // By the spec, window managers can set the width/height to uint32_t max value (or -1 as we
        // do not have unsigned types) to indicate that there are constraints on the extent size.
        final int currentWidth = capabilities.currentExtent().width();
        final int currentHeight = capabilities.currentExtent().height();
        if (currentWidth != -1 && currentHeight != -1) {
            return capabilities.currentExtent();
        }

        final var pWindowWidth = stack.mallocInt(1);
        final var pWindowHeight = stack.mallocInt(1);
        glfwGetFramebufferSize(windowHandle, pWindowWidth, pWindowHeight);
        return VkExtent2D.callocStack(stack)
                         .width(Math.max(capabilities.minImageExtent().width(),
                                         Math.min(capabilities.maxImageExtent().width(),
                                                  pWindowWidth.get(0))))
                         .height(Math.max(capabilities.minImageExtent().height(),
                                          Math.min(capabilities.maxImageExtent().height(),
                                                   pWindowHeight.get(0))));
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
     */
    @Override
    public void recreate() {
        if (!this.cleanedUp) {
            throw new IllegalStateException("Tried re-create swapchain without cleaning up first!");
        }

        try (var stack = stackPush()) {
            final var createInfo = createSwapChainCreateInfo(stack,
                                                             this.deviceContext,
                                                             this.surface,
                                                             this.windowHandle);

            final var pSwapChain = stack.mallocLong(1);
            final var error = vkCreateSwapchainKHR(this.deviceContext.getDeviceHandle(), createInfo, null, pSwapChain);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Creating swapchain failed: "
                                                        + translateVulkanResult(error));
            }

            this.swapchain = pSwapChain.get(0);
            this.imageFormat = createInfo.imageFormat();
            this.extent.set(createInfo.imageExtent());
        }

        try (var stack = stackPush()) {
            final var imageCount = stack.mallocInt(1);
            final var countError = vkGetSwapchainImagesKHR(this.deviceContext.getDeviceHandle(),
                                                           this.swapchain,
                                                           imageCount,
                                                           null);
            if (countError != VK_SUCCESS) {
                throw new IllegalStateException("Could not get swapchain image count: "
                                                        + translateVulkanResult(countError));
            }

            final var pSwapchainImages = stack.mallocLong(imageCount.get(0));
            final var fetchError = vkGetSwapchainImagesKHR(this.deviceContext.getDeviceHandle(),
                                                           this.swapchain,
                                                           imageCount,
                                                           pSwapchainImages);
            if (fetchError != VK_SUCCESS) {
                throw new IllegalStateException("Could not fetch swapchain images: "
                                                        + translateVulkanResult(fetchError));
            }

            final long[] images = new long[imageCount.get(0)];
            this.imageViews = new GPUImage.View[imageCount.get(0)];
            pSwapchainImages.get(images);

            for (var i = 0; i < imageCount.get(0); ++i) {
                this.imageViews[i] = new GPUImage.View(this.deviceContext.getDeviceHandle(),
                                                       images[i],
                                                       this.imageFormat,
                                                       VK_IMAGE_ASPECT_COLOR_BIT);
            }
        }
        this.cleanedUp = false;
    }

    /**
     * Releases resources in preparations for re-creation or shutdown.
     */
    @Override
    public void cleanup() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried cleanup already cleared swapchain!");
        }

        if (this.swapchain != VK_NULL_HANDLE) {
            for (final var imageView : this.imageViews) {
                imageView.close();
            }

            vkDestroySwapchainKHR(this.deviceContext.getDeviceHandle(), this.swapchain, null);
        }
        this.cleanedUp = true;
    }
}
