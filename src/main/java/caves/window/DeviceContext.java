package caves.window;

import caves.window.rendering.SwapChainSupportDetails;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class DeviceContext implements AutoCloseable {
    private final VkPhysicalDevice physicalDevice;
    private final DeviceAndQueueFamilies deviceAndQueueFamilies;

    /**
     * Gets the chosen physical device.
     *
     * @return the physical device
     */
    public VkPhysicalDevice getPhysicalDevice() {
        return this.physicalDevice;
    }

    /**
     * Gets the active logical device. Can be used to fetch queues.
     *
     * @return the logical device
     */
    public VkDevice getDevice() {
        return this.deviceAndQueueFamilies.getDevice();
    }

    /**
     * Gets the graphics queue family index.
     *
     * @return the stored graphics queue family index
     */
    public int getGraphicsQueueFamilyIndex() {
        return this.deviceAndQueueFamilies.getGraphicsFamily();
    }

    /**
     * Gets the presentation queue family index.
     *
     * @return the stored presentation queue family index
     */
    public int getPresentationQueueFamilyIndex() {
        return this.deviceAndQueueFamilies.getPresentationFamily();
    }

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
     * @param instance         instance to select the device for
     * @param surface          window surface to use
     * @param deviceExtensions required device extensions
     *
     * @return device context for the given vulkan instance
     */
    public static DeviceContext getForInstance(
            final VulkanInstance instance,
            final long surface,
            final ByteBuffer[] deviceExtensions
    ) {
        try (var stack = stackPush()) {
            final var pPhysicalDeviceCount = getPhysicalDeviceCount(instance, stack);
            final var pPhysicalDevices = getPhysicalDevices(instance, stack, pPhysicalDeviceCount);
            return createContext(stack,
                                 pPhysicalDevices,
                                 instance.getVkInstance(),
                                 surface,
                                 deviceExtensions);
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
            final VkInstance instance,
            final long surface,
            final ByteBuffer[] deviceExtensions
    ) {
        // Just naively select the first suitable device
        // TODO: Sort by device suitability and select most suitable
        VkPhysicalDevice selected = null;
        QueueIndices indices = null;
        while (pPhysicalDevices.hasRemaining()) {
            final var device = new VkPhysicalDevice(pPhysicalDevices.get(), instance);

            indices = new QueueIndices(stack, surface, device);
            if (isSuitableDevice(stack, device, indices, deviceExtensions, surface)) {
                selected = device;
                break;
            }
        }

        if (selected == null || !indices.isComplete()) {
            throw new IllegalStateException("Could not find suitable physical device!");
        }
        return new DeviceContext(selected, new DeviceAndQueueFamilies(selected, indices, deviceExtensions));
    }

    // TODO: Return "suitability value" instead of a boolean
    private static boolean isSuitableDevice(
            final MemoryStack stack,
            final VkPhysicalDevice device,
            final QueueIndices indices,
            final ByteBuffer[] requiredExtensions,
            final long surface
    ) {
        final var extensionsSupported = checkDeviceExtensionSupport(stack, device, requiredExtensions);
        var swapChainAdequate = false;
        if (extensionsSupported) {
            final var swapChainSupport = SwapChainSupportDetails.querySupport(device, surface);
            swapChainAdequate = !swapChainSupport.getSurfaceFormats().isEmpty()
                    && !swapChainSupport.getPresentModes().isEmpty();
        }

        if (!indices.isComplete() || !extensionsSupported || !swapChainAdequate) {
            return false;
        }

        final var deviceProperties = VkPhysicalDeviceProperties.mallocStack(stack);
        final var deviceFeatures = VkPhysicalDeviceFeatures.mallocStack(stack);
        vkGetPhysicalDeviceProperties(device, deviceProperties);
        vkGetPhysicalDeviceFeatures(device, deviceFeatures);

        return true;
    }

    private static boolean checkDeviceExtensionSupport(
            final MemoryStack stack,
            final VkPhysicalDevice device,
            final ByteBuffer[] requiredExtensions
    ) {
        final var extensionCount = stack.mallocInt(1);
        vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, extensionCount, null);

        final var availableExtensions = VkExtensionProperties.callocStack(extensionCount.get(0), stack);
        vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, extensionCount, availableExtensions);

        for (var requiredExtension : requiredExtensions) {
            requiredExtension.mark();
            final var extensionName = StandardCharsets.UTF_8.decode(requiredExtension)
                                                            .toString()
                                                            .trim(); // HACK: .trim removes null-terminators etc.
            requiredExtension.reset();

            var found = false;
            for (var extension : availableExtensions) {
                if (extensionName.equals(extension.extensionNameString())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                System.out.printf("Validation layer \"%s\" not found.", extensionName);
                return false;
            }
        }

        return true;
    }

    @Override
    public void close() {
        // Physical device is automatically destroyed with the instance so do not destroy it here
        this.deviceAndQueueFamilies.close();
    }
}
