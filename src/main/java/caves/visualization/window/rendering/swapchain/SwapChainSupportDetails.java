package caves.visualization.window.rendering.swapchain;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAllocInt;
import static org.lwjgl.vulkan.KHRSurface.*;

public final class SwapChainSupportDetails implements AutoCloseable {
    private final VkSurfaceCapabilitiesKHR surfaceCapabilities;
    private final List<VkSurfaceFormatKHR> surfaceFormats;
    private final List<Integer> presentModes;

    /**
     * Gets the surface capabilities suported by a device. This information can then be used to
     * determine what kind of images the surface supports.
     *
     * @return the surface capabilities
     */
    public VkSurfaceCapabilitiesKHR getSurfaceCapabilities() {
        return this.surfaceCapabilities;
    }

    /**
     * Gets the surface formats supported by a device.
     *
     * @return the surface formats
     */
    public List<VkSurfaceFormatKHR> getSurfaceFormats() {
        return this.surfaceFormats;
    }

    /**
     * Gets the supported presentation modes for a device.
     *
     * @return the presentation modes
     */
    public List<Integer> getPresentModes() {
        return this.presentModes;
    }

    private SwapChainSupportDetails(
            final VkSurfaceCapabilitiesKHR surfaceCapabilities,
            final List<VkSurfaceFormatKHR> surfaceFormats,
            final List<Integer> presentModes
    ) {
        this.surfaceCapabilities = surfaceCapabilities;
        this.surfaceFormats = surfaceFormats;
        this.presentModes = presentModes;
    }

    /**
     * Queries the device for swapchain support details.
     *
     * @param device  device the query
     * @param surface surface to be used
     *
     * @return details on swapchain features and limits
     */
    public static SwapChainSupportDetails querySupport(
            final VkPhysicalDevice device,
            final long surface
    ) {
        try (var stack = stackPush()) {
            final var pSurfaceCapabilities = getSurfaceCapabilities(device, surface);
            final var surfaceFormats = getSurfaceFormats(stack, device, surface);
            final var presentModes = getPresentModes(stack, device, surface);
            return new SwapChainSupportDetails(pSurfaceCapabilities, surfaceFormats, presentModes);
        }
    }

    private static List<Integer> getPresentModes(
            final MemoryStack stack,
            final VkPhysicalDevice device,
            final long surface
    ) {
        final var presentModeCount = stack.mallocInt(1);
        vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, presentModeCount, null);

        final var presentModeBuffer = memAllocInt(presentModeCount.get(0));
        vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, presentModeCount, presentModeBuffer);
        return IntStream.range(0, presentModeCount.get(0))
                        .mapToObj(presentModeBuffer::get)
                        .collect(Collectors.toList());
    }

    private static List<VkSurfaceFormatKHR> getSurfaceFormats(
            final MemoryStack stack,
            final VkPhysicalDevice device,
            final long surface
    ) {
        final var formatCount = stack.mallocInt(1);
        vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, formatCount, null);

        final List<VkSurfaceFormatKHR> surfaceFormats;
        if (formatCount.get(0) > 0) {
            final var surfaceFormatBuffer = VkSurfaceFormatKHR.calloc(formatCount.get(0));
            vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, formatCount, surfaceFormatBuffer);

            surfaceFormats = surfaceFormatBuffer.stream().collect(Collectors.toList());
        } else {
            surfaceFormats = List.of();
        }
        return surfaceFormats;
    }

    private static VkSurfaceCapabilitiesKHR getSurfaceCapabilities(
            final VkPhysicalDevice device,
            final long surface
    ) {
        final var pSurfaceCapabilities = VkSurfaceCapabilitiesKHR.calloc();
        vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surface, pSurfaceCapabilities);
        return pSurfaceCapabilities;
    }

    @Override
    public void close() {
        this.surfaceCapabilities.close();
        for (final var format : this.surfaceFormats) {
            format.close();
        }
    }
}
