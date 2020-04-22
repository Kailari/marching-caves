package caves.visualization.window;

import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import javax.annotation.Nullable;

import static caves.util.profiler.Profiler.PROFILER;
import static org.lwjgl.system.MemoryStack.stackGet;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.VK10.*;

public class QueueFamilies {
    @Nullable private final Integer graphics;
    @Nullable private final Integer present;
    @Nullable private final Integer transfer;

    /**
     * Gets the index for the graphics queue family. Calling this is generally safe, but during
     * initialization care must be taken not to call the getters without checking with {@link
     * #isComplete()} first.
     *
     * @return the graphics queue family index
     */
    public int getGraphics() {
        assert this.graphics != null;
        return this.graphics;
    }

    /**
     * Gets the index for the presentation queue family. Calling this is generally safe, but during
     * initialization care must be taken not to call the getters without checking with {@link
     * #isComplete()} first.
     *
     * @return the presentation queue family index
     */
    public int getPresent() {
        assert this.present != null;
        return this.present;
    }

    /**
     * Gets the index for the transfer queue family. Calling this is generally safe, but during
     * initialization care must be taken not to call the getters without checking with {@link
     * #isComplete()} first.
     *
     * @return the presentation queue family index
     */
    public int getTransfer() {
        assert this.transfer != null;
        return this.transfer;
    }

    /**
     * Checks if all queue indices have been populated. Calling queue index getters is not safe
     * unless this check returns <code>true</code>
     *
     * @return <code>true</code> if all indices are present, <code>false</code> otherwise
     */
    public boolean isComplete() {
        return this.graphics != null && this.present != null && this.transfer != null;
    }

    /**
     * Tries to find as many required queue families for the given device as possible.
     *
     * @param surface        handle to the window surface
     * @param physicalDevice physical device to fetch the queues from
     */
    public QueueFamilies(final long surface, final VkPhysicalDevice physicalDevice) {
        try (var stack = stackPush()) {
            final var queueProps = getQueueFamilyProperties(physicalDevice);
            final var queueFamilyCount = queueProps.capacity();

            int selectedGraphics = 0;
            int selectedTransfer = 0;
            int selectedPresent = 0;
            boolean graphicsFound = false;
            boolean transferFound = false;
            boolean presentFound = false;

            final var pSupported = stack.mallocInt(1);
            for (var i = 0; i < queueFamilyCount; ++i) {
                final var hasGraphicsBit = (queueProps.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0;
                if (!graphicsFound && hasGraphicsBit) {
                    selectedGraphics = i;
                    graphicsFound = true;
                }

                final var hasTransferBit = (queueProps.get(i).queueFlags() & VK_QUEUE_TRANSFER_BIT) != 0;
                if (!transferFound && !hasGraphicsBit && hasTransferBit) {
                    selectedTransfer = i;
                    transferFound = true;
                }

                if (!presentFound) {
                    vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, surface, pSupported);
                    if (pSupported.get(0) == VK_TRUE) {
                        presentFound = true;
                        selectedPresent = i;
                    }
                }

                final var allFound = graphicsFound && presentFound && transferFound;
                if (allFound) {
                    break;
                }
            }

            if (graphicsFound) {
                if (!transferFound && (queueProps.get(selectedGraphics).queueFlags() & VK_QUEUE_TRANSFER_BIT) != 0) {
                    PROFILER.log("WARN: No separate transfer queue available, falling back to graphics queue!");
                    selectedTransfer = selectedGraphics;
                    transferFound = true;
                }
            }

            this.graphics = graphicsFound ? selectedGraphics : null;
            this.transfer = transferFound ? selectedTransfer : null;
            this.present = presentFound ? selectedPresent : null;
        }
    }

    private static VkQueueFamilyProperties.Buffer getQueueFamilyProperties(
            final VkPhysicalDevice physicalDevice
    ) {
        final var stack = stackGet();
        final var pCount = stack.mallocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pCount, null);

        final var pQueueFamilyProperties = VkQueueFamilyProperties.callocStack(pCount.get(0));
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pCount, pQueueFamilyProperties);
        return pQueueFamilyProperties;
    }
}
