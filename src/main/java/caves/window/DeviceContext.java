package caves.window;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

import java.nio.IntBuffer;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class DeviceContext implements AutoCloseable {
    private final VkPhysicalDevice physicalDevice;
    private final DeviceAndQueueFamilies deviceAndQueueFamilies;

    private DeviceContext(
            final VkPhysicalDevice physicalDevice,
            final DeviceAndQueueFamilies deviceAndQueueFamilies
    ) {
        this.physicalDevice = physicalDevice;
        this.deviceAndQueueFamilies = deviceAndQueueFamilies;
    }

    /**
     * Selects a new physical device and creates context for it using the given Vulkan instance.
     *
     * @param instance instance to select the device for
     *
     * @return device context for the given vulkan instance
     */
    public static DeviceContext getForInstance(final VulkanInstance instance) {
        try (var stack = stackPush()) {
            final var pPhysicalDeviceCount = getPhysicalDeviceCount(instance, stack);
            final var pPhysicalDevices = getPhysicalDevices(instance, stack, pPhysicalDeviceCount);
            return createContext(stack, pPhysicalDevices, instance.getVkInstance());
        }
    }

    private static IntBuffer getPhysicalDeviceCount(
            final VulkanInstance instance,
            final MemoryStack stack
    ) {
        final var pPhysicalDeviceCount = stack.mallocInt(1);

        // Enumerate with `null` ptr at first as we do not know the device count yet.
        final var error = vkEnumeratePhysicalDevices(instance.getVkInstance(), pPhysicalDeviceCount, null);
        if (error != VK_SUCCESS) {
            throw new IllegalStateException("Fetching number of physical devices failed: "
                                                    + translateVulkanResult(error));
        }
        return pPhysicalDeviceCount;
    }

    private static PointerBuffer getPhysicalDevices(
            final VulkanInstance instance,
            final MemoryStack stack,
            final IntBuffer pPhysicalDeviceCount
    ) {
        // We know the count, enumerate with pointer buffer reserved for devices
        final var pPhysicalDevices = stack.mallocPointer(pPhysicalDeviceCount.get(0));
        final var error = vkEnumeratePhysicalDevices(instance.getVkInstance(), pPhysicalDeviceCount, pPhysicalDevices);
        if (error != VK_SUCCESS) {
            throw new IllegalStateException("Fetching a physical device failed: "
                                                    + translateVulkanResult(error));
        }
        return pPhysicalDevices;
    }

    private static DeviceContext createContext(
            final MemoryStack stack,
            final PointerBuffer pPhysicalDevices,
            final VkInstance instance
    ) {
        // Just naively select the first suitable device
        // TODO: Sort by device suitability and select most suitable
        VkPhysicalDevice selected = null;
        QueueIndices indices = null;
        while (pPhysicalDevices.hasRemaining()) {
            final var device = new VkPhysicalDevice(pPhysicalDevices.get(), instance);

            indices = new QueueIndices(stack, device);
            if (isSuitableDevice(stack, device, indices)) {
                selected = device;
                break;
            }
        }

        if (selected == null || !indices.isComplete()) {
            throw new IllegalStateException("Could not find suitable physical device!");
        }
        return new DeviceContext(selected, new DeviceAndQueueFamilies(selected, indices));
    }

    // TODO: Return "suitability value" instead of boolean
    private static boolean isSuitableDevice(
            final MemoryStack stack,
            final VkPhysicalDevice device,
            final QueueIndices indices
    ) {
        if (!indices.isComplete()) {
            return false;
        }

        final var deviceProperties = VkPhysicalDeviceProperties.mallocStack(stack);
        final var deviceFeatures = VkPhysicalDeviceFeatures.mallocStack(stack);
        vkGetPhysicalDeviceProperties(device, deviceProperties);
        vkGetPhysicalDeviceFeatures(device, deviceFeatures);

        return true;
    }

    @Override
    public void close() {
        // Physical device is automatically destroyed with the instance so do not destroy it here
        this.deviceAndQueueFamilies.close();
    }
}
