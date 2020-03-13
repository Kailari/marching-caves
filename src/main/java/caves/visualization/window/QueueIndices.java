package caves.visualization.window;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import javax.annotation.Nullable;
import java.nio.IntBuffer;

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
     * @param stack          memory stack to use for short-lived allocations
     * @param surface        handle to the window surface
     * @param physicalDevice physical device to fetch the queues from
     */
    public QueueIndices(
            final MemoryStack stack,
            final long surface,
            final VkPhysicalDevice physicalDevice
    ) {
        final var pQueueFamilyPropertyCount = getQueueFamilyPropertyCount(stack, physicalDevice);
        final var queueProps = getQueueFamilyProperties(stack, physicalDevice, pQueueFamilyPropertyCount);

        int selectedGraphics = 0;
        int selectedPresentation = 0;
        boolean graphicsFound = false;
        boolean presentationFound = false;
        final var pSupported = stack.mallocInt(1);
        for (var i = 0; i < pQueueFamilyPropertyCount.get(0); ++i) {
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

        this.graphics = graphicsFound ? selectedGraphics : null;
        this.presentation = presentationFound ? selectedPresentation : null;
    }

    private static VkQueueFamilyProperties.Buffer getQueueFamilyProperties(
            final MemoryStack stack,
            final VkPhysicalDevice physicalDevice,
            final IntBuffer pQueueFamilyPropertyCount
    ) {
        final var queueProps = VkQueueFamilyProperties.callocStack(pQueueFamilyPropertyCount.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice,
                                                 pQueueFamilyPropertyCount,
                                                 queueProps);
        return queueProps;
    }

    private static IntBuffer getQueueFamilyPropertyCount(
            final MemoryStack stack,
            final VkPhysicalDevice physicalDevice
    ) {
        final var pQueueFamilyPropertyCount = stack.mallocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice,
                                                 pQueueFamilyPropertyCount,
                                                 null);
        return pQueueFamilyPropertyCount;
    }
}
