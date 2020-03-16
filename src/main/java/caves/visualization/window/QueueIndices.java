package caves.visualization.window;

import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import javax.annotation.Nullable;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.VK10.*;

public class QueueIndices {
    @Nullable private final Integer graphics;
    @Nullable private final Integer presentation;

    /**
     * Gets the graphics family. Calling this is generally safe, but during initialization care must
     * be taken not to call the getters without checking with {@link #isComplete()} first.
     *
     * @return the graphics queue family index
     */
    public int getGraphicsFamily() {
        assert this.graphics != null;
        return this.graphics;
    }

    /**
     * Gets the presentation family. Calling this is generally safe, but during initialization care
     * must be taken not to call the getters without checking with {@link #isComplete()} first.
     *
     * @return the presentation queue family index
     */
    public int getPresentationFamily() {
        assert this.presentation != null;
        return this.presentation;
    }

    /**
     * Checks if all queue indices have been populated. Calling queue index getters is not safe
     * unless this check returns <code>true</code>
     *
     * @return <code>true</code> if all indices are present, <code>false</code> otherwise
     */
    public boolean isComplete() {
        return this.graphics != null && this.presentation != null;
    }

    /**
     * Tries to find as many required queue families for the given device as possible.
     *
     * @param surface        handle to the window surface
     * @param physicalDevice physical device to fetch the queues from
     */
    public QueueIndices(final long surface, final VkPhysicalDevice physicalDevice) {
        final var queueProps = getQueueFamilyProperties(physicalDevice);
        final var queueFamilyCount = queueProps.capacity();

        int selectedGraphics = 0;
        int selectedPresentation = 0;
        boolean graphicsFound = false;
        boolean presentationFound = false;

        try (var stack = stackPush()) {
            final var pSupported = stack.mallocInt(1);
            for (var i = 0; i < queueFamilyCount; ++i) {
                if (!graphicsFound && (queueProps.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    selectedGraphics = i;
                    graphicsFound = true;
                }
                if (!presentationFound) {
                    vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, surface, pSupported);
                    if (pSupported.get(0) == VK_TRUE) {
                        presentationFound = true;
                        selectedPresentation = i;
                    }
                }

                final var allFound = graphicsFound && presentationFound;
                if (allFound) {
                    break;
                }
            }
        }

        this.graphics = graphicsFound ? selectedGraphics : null;
        this.presentation = presentationFound ? selectedPresentation : null;
    }

    private static VkQueueFamilyProperties.Buffer getQueueFamilyProperties(
            final VkPhysicalDevice physicalDevice
    ) {
        try (var stack = stackPush()) {
            final var pCount = stack.mallocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pCount, null);

            final var pQueueFamilyProperties = VkQueueFamilyProperties.callocStack(pCount.get(0));
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pCount, pQueueFamilyProperties);
            return pQueueFamilyProperties;
        }
    }
}
