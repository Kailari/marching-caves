package caves.visualization.window;

import caves.visualization.util.io.BufferUtil;
import caves.visualization.window.rendering.SwapChainSupportDetails;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Optional;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class DeviceContext implements AutoCloseable {
    private final VkPhysicalDevice physicalDevice;
    private final VkPhysicalDeviceMemoryProperties memoryProperties;

    private final DeviceAndQueueFamilies deviceAndQueueFamilies;
    private final VkQueue graphicsQueue;
    private final VkQueue presentQueue;

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
        return this.deviceAndQueueFamilies.getPresentFamily();
    }

    /**
     * Gets the memory properties of the associated physical device.
     *
     * @return the memory properties
     */
    public VkPhysicalDeviceMemoryProperties getMemoryProperties() {
        return this.memoryProperties;
    }

    /**
     * Gets the graphics queue for submitting graphics command buffers.
     *
     * @return the graphics queue
     */
    public VkQueue getGraphicsQueue() {
        return this.graphicsQueue;
    }

    /**
     * Gets the present queue for presenting swapchain images.
     *
     * @return the present queue
     */
    public VkQueue getPresentQueue() {
        return this.graphicsQueue;
    }

    private DeviceContext(
            final VkPhysicalDevice physicalDevice,
            final DeviceAndQueueFamilies deviceAndQueueFamilies
    ) {
        this.physicalDevice = physicalDevice;
        this.memoryProperties = VkPhysicalDeviceMemoryProperties.malloc();
        vkGetPhysicalDeviceMemoryProperties(this.physicalDevice, this.memoryProperties);

        this.deviceAndQueueFamilies = deviceAndQueueFamilies;

        this.graphicsQueue = getQueue(this.getDevice(), this.deviceAndQueueFamilies.getGraphicsFamily());
        this.presentQueue = getQueue(this.getDevice(), this.deviceAndQueueFamilies.getPresentFamily());
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
            final PointerBuffer deviceExtensions
    ) {
        try (var stack = stackPush()) {
            final var pPhysicalDeviceCount = getPhysicalDeviceCount(instance, stack);
            final var pPhysicalDevices = getPhysicalDevices(instance, stack, pPhysicalDeviceCount);
            return createContext(stack,
                                 pPhysicalDevices,
                                 instance.getInstance(),
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
        final var error = vkEnumeratePhysicalDevices(instance.getInstance(), pPhysicalDeviceCount, null);
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
        final var error = vkEnumeratePhysicalDevices(instance.getInstance(), pPhysicalDeviceCount, pPhysicalDevices);
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
            final PointerBuffer deviceExtensions
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
            final PointerBuffer requiredExtensions,
            final long surface
    ) {
        final var extensionsSupported = checkDeviceExtensionSupport(stack, device, requiredExtensions);
        final var swapChainAdequate = extensionsSupported && isSwapChainAdequate(device, surface);

        if (!indices.isComplete() || !extensionsSupported || !swapChainAdequate) {
            return false;
        }

        final var deviceProperties = VkPhysicalDeviceProperties.mallocStack(stack);
        final var deviceFeatures = VkPhysicalDeviceFeatures.mallocStack(stack);
        vkGetPhysicalDeviceProperties(device, deviceProperties);
        vkGetPhysicalDeviceFeatures(device, deviceFeatures);

        return true;
    }

    private static boolean isSwapChainAdequate(final VkPhysicalDevice device, final long surface) {
        final var swapChainSupport = SwapChainSupportDetails.querySupport(device, surface);
        final var hasSurfaceFormats = !swapChainSupport.getSurfaceFormats().isEmpty();
        final var hasPresentModes = !swapChainSupport.getPresentModes().isEmpty();

        return hasSurfaceFormats && hasPresentModes;
    }

    private static boolean checkDeviceExtensionSupport(
            final MemoryStack stack,
            final VkPhysicalDevice device,
            final PointerBuffer requiredExtensions
    ) {
        final var extensionCount = stack.mallocInt(1);
        vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, extensionCount, null);

        final var availableExtensions = VkExtensionProperties.callocStack(extensionCount.get(0), stack);
        vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, extensionCount, availableExtensions);

        return !BufferUtil.filteredForEachAsStringUTF8(
                requiredExtensions,
                name -> availableExtensions.stream()
                                           .map(VkExtensionProperties::extensionNameString)
                                           .noneMatch(name::equals),
                notFound -> {
                    /* this is potentially called for all available devices and not finding
                       extensions on all devices is expected, so do not log errors here     */
                });
    }

    private static VkQueue getQueue(final VkDevice device, final int queueFamilyIndex) {
        try (var stack = stackPush()) {
            final var pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device, queueFamilyIndex, 0, pQueue);
            return new VkQueue(pQueue.get(0), device);
        }
    }

    @Override
    public void close() {
        // Physical device is automatically destroyed with the instance so do not destroy it here
        this.memoryProperties.free();
        this.deviceAndQueueFamilies.close();
    }

    public Optional<Integer> findSuitableMemoryType(final int typeFilter, final int propertyFlags) {
        for (var i = 0; i < this.memoryProperties.memoryTypeCount(); ++i) {
            final var typeIsSuitable = (typeFilter & (1 << i)) != 0;
            final var propertiesAreSuitable =
                    (this.memoryProperties.memoryTypes(i).propertyFlags() & propertyFlags) == propertyFlags;

            if (typeIsSuitable && propertiesAreSuitable) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }
}
