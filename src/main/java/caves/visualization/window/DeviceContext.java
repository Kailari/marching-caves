package caves.visualization.window;

import caves.visualization.memory.GPUMemoryAllocator;
import caves.visualization.rendering.swapchain.SwapChainSupportDetails;
import caves.visualization.util.io.BufferUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.Optional;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class DeviceContext implements AutoCloseable {
    private final VkPhysicalDevice physicalDevice;
    private final VkPhysicalDeviceMemoryProperties memoryProperties;

    private final LogicalDevice logicalDevice;
    private final VkQueue graphicsQueue;
    private final VkQueue presentQueue;
    private final VkQueue transferQueue;

    private final GPUMemoryAllocator memoryAllocator;

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
    public VkDevice getDeviceHandle() {
        return this.logicalDevice.getHandle();
    }

    /**
     * Gets the queue families on the active logical device.
     *
     * @return the queue families in use
     */
    public QueueFamilies getQueueFamilies() {
        return this.logicalDevice.getQueueFamilies();
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
        return this.presentQueue;
    }

    /**
     * Gets the transfer queue for submitting transfer command buffers.
     *
     * @return the transfer queue
     */
    public VkQueue getTransferQueue() {
        return this.transferQueue;
    }

    /**
     * Gets the memory manager/allocator. Use this to allocate memory for buffers and images.
     *
     * @return the memory manager
     */
    public GPUMemoryAllocator getMemoryAllocator() {
        return this.memoryAllocator;
    }

    /**
     * Selects a new physical device and creates context for it using the given Vulkan instance.
     *
     * @param instance           instance to select the device for
     * @param surface            window surface to use
     * @param requiredExtensions required device extensions
     */
    public DeviceContext(
            final VulkanInstance instance,
            final long surface,
            final PointerBuffer requiredExtensions
    ) {
        try (var stack = stackPush()) {
            final var pPhysicalDevices = getPhysicalDevices(stack, instance.getInstance());
            // Just naively select the first suitable device
            // TODO: Sort by device suitability and select most suitable
            VkPhysicalDevice selected = null;
            QueueFamilies indices = null;
            while (pPhysicalDevices.hasRemaining()) {
                final var device = new VkPhysicalDevice(pPhysicalDevices.get(), instance.getInstance());

                indices = new QueueFamilies(surface, device);
                if (isSuitableDevice(device, indices, requiredExtensions, surface)) {
                    selected = device;
                    break;
                }
            }

            if (selected == null || !indices.isComplete()) {
                throw new IllegalStateException("Could not find suitable physical device!");
            }

            this.physicalDevice = selected;
            this.logicalDevice = new LogicalDevice(selected, indices, requiredExtensions);
        }

        this.graphicsQueue = getQueue(this.getDeviceHandle(),
                                      this.logicalDevice.getQueueFamilies().getGraphics());
        this.transferQueue = getQueue(this.getDeviceHandle(),
                                      this.logicalDevice.getQueueFamilies().getTransfer());
        this.presentQueue = getQueue(this.getDeviceHandle(),
                                     this.logicalDevice.getQueueFamilies().getPresent());

        this.memoryProperties = VkPhysicalDeviceMemoryProperties.malloc();
        vkGetPhysicalDeviceMemoryProperties(this.physicalDevice, this.memoryProperties);
        this.memoryAllocator = new GPUMemoryAllocator(this.memoryProperties, this.logicalDevice);
    }

    private static PointerBuffer getPhysicalDevices(
            final MemoryStack stack,
            final VkInstance instance
    ) {
        final var pCount = stack.mallocInt(1);
        final var fetchCountResult = vkEnumeratePhysicalDevices(instance, pCount, null);
        if (fetchCountResult != VK_SUCCESS) {
            throw new IllegalStateException("Fetching number of physical devices failed: "
                                                    + translateVulkanResult(fetchCountResult));
        }

        // We know the count, enumerate with pointer buffer reserved for devices
        final var pPhysicalDevices = stack.mallocPointer(pCount.get(0));
        final var fetchDevicesResult = vkEnumeratePhysicalDevices(instance, pCount, pPhysicalDevices);
        if (fetchDevicesResult != VK_SUCCESS) {
            throw new IllegalStateException("Fetching a physical device failed: "
                                                    + translateVulkanResult(fetchDevicesResult));
        }

        return pPhysicalDevices;
    }

    // TODO: Return "suitability value" instead of a boolean
    private static boolean isSuitableDevice(
            final VkPhysicalDevice device,
            final QueueFamilies indices,
            final PointerBuffer requiredExtensions,
            final long surface
    ) {
        final var extensionsSupported = checkDeviceExtensionSupport(device, requiredExtensions);
        final var swapChainAdequate = extensionsSupported && isSwapChainAdequate(device, surface);

        if (!indices.isComplete() || !extensionsSupported || !swapChainAdequate) {
            return false;
        }

        try (var stack = stackPush()) {
            final var deviceProperties = VkPhysicalDeviceProperties.mallocStack(stack);
            final var deviceFeatures = VkPhysicalDeviceFeatures.mallocStack(stack);
            vkGetPhysicalDeviceProperties(device, deviceProperties);
            vkGetPhysicalDeviceFeatures(device, deviceFeatures);

            // adjust suitability here
        }

        return true;
    }

    private static boolean isSwapChainAdequate(final VkPhysicalDevice device, final long surface) {
        final var swapChainSupport = SwapChainSupportDetails.querySupport(device, surface);
        final var hasSurfaceFormats = !swapChainSupport.getSurfaceFormats().isEmpty();
        final var hasPresentModes = !swapChainSupport.getPresentModes().isEmpty();

        return hasSurfaceFormats && hasPresentModes;
    }

    private static boolean checkDeviceExtensionSupport(
            final VkPhysicalDevice device,
            final PointerBuffer requiredExtensions
    ) {
        try (var stack = stackPush()) {
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
        // NOTE: Physical device is automatically destroyed with the instance so do not destroy it here
        this.memoryProperties.free();
        this.memoryAllocator.close();
        this.logicalDevice.close();
    }

    /**
     * Finds a supported image format matching given criteria.
     *
     * @param candidates   format candidates to test
     * @param tiling       desired tiling features
     * @param featureFlags desired feature flags
     *
     * @return supported format or empty optional if none is found
     */
    public Optional<Integer> findImageFormat(
            final int[] candidates,
            final int tiling,
            final int featureFlags
    ) {
        try (var ignored = stackPush()) {
            for (final var format : candidates) {
                final var pProps = VkFormatProperties.callocStack();
                vkGetPhysicalDeviceFormatProperties(this.physicalDevice, format, pProps);

                final var hasTilingBits = (pProps.linearTilingFeatures() & featureFlags) == featureFlags;
                final var hasOptimalBits = (pProps.optimalTilingFeatures() & featureFlags) == featureFlags;
                if (tiling == VK_IMAGE_TILING_LINEAR && hasTilingBits) {
                    return Optional.of(format);
                } else if (tiling == VK_IMAGE_TILING_OPTIMAL && hasOptimalBits) {
                    return Optional.of(format);
                }
            }
        }

        return Optional.empty();
    }
}
