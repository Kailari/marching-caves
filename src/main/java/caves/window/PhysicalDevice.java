package caves.window;

import org.lwjgl.vulkan.VkPhysicalDevice;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkEnumeratePhysicalDevices;

public class PhysicalDevice {
    private final VkPhysicalDevice physicalDevice;

    /**
     * Selects a new physical device for the given Vulkan instance.
     *
     * @param instance instance to select the device for
     */
    public PhysicalDevice(final VulkanInstance instance) {
        try (var stack = stackPush()) {
            final var pPhysicalDeviceCount = stack.mallocInt(1);

            // Enumerate with `null` ptr at first as we do not know the device count yet.
            var error = vkEnumeratePhysicalDevices(instance.getVkInstance(), pPhysicalDeviceCount, null);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Fetching number of physical devices failed: "
                                                        + translateVulkanResult(error));
            }

            // Enumerate again with the count
            final var pPhysicalDevices = stack.mallocPointer(pPhysicalDeviceCount.get(0));
            error = vkEnumeratePhysicalDevices(instance.getVkInstance(), pPhysicalDeviceCount, pPhysicalDevices);

            // Just naively select the first device
            final var physicalDevice = pPhysicalDevices.get(0);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Fetching a physical device failed: "
                                                        + translateVulkanResult(error));
            }

            this.physicalDevice = new VkPhysicalDevice(physicalDevice, instance.getVkInstance());
        }
    }

    /**
     * Gets the underlying native vulkan physical device.
     *
     * @return the vulkan physical device
     */
    public VkPhysicalDevice getVkPhysicalDevice() {
        return physicalDevice;
    }
}
