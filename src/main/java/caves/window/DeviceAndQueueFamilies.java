package caves.window;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.List;
import java.util.TreeSet;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;

public final class DeviceAndQueueFamilies implements AutoCloseable {
    private final VkDevice device;
    private final VkPhysicalDeviceMemoryProperties memoryProperties;

    private final int graphicsFamily;
    private final int presentationFamily;

    /**
     * Gets the active logical device.
     *
     * @return the logical device
     */
    public VkDevice getDevice() {
        return this.device;
    }

    /**
     * Gets the stored graphics queue family index.
     *
     * @return the stored graphics queue family index
     */
    public int getGraphicsFamily() {
        return graphicsFamily;
    }

    /**
     * Gets the stored presentation queue family index.
     *
     * @return the stored presentation queue family index
     */
    public int getPresentationFamily() {
        return presentationFamily;
    }

    /**
     * Selects a suitable device and graphics queue family from the physical device.
     *
     * @param physicalDevice the physical device to use
     * @param indices        queue indices to use
     */
    public DeviceAndQueueFamilies(
            final VkPhysicalDevice physicalDevice,
            final QueueIndices indices
    ) {
        if (!indices.isComplete()) {
            throw new IllegalArgumentException("Tried to create a logical device for incomplete queue indices!");
        }

        try (var stack = stackPush()) {
            final VkDeviceCreateInfo deviceCreateInfo = createDeviceCreateInfo(indices, stack);
            final var pDevice = stack.mallocPointer(1);
            final var error = vkCreateDevice(physicalDevice,
                                             deviceCreateInfo,
                                             null,
                                             pDevice);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Creating a logical device failed: "
                                                        + translateVulkanResult(error));
            }
            final var device = pDevice.get(0);

            final var memoryProperties = VkPhysicalDeviceMemoryProperties.callocStack(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice,
                                                memoryProperties);

            this.device = new VkDevice(device, physicalDevice, deviceCreateInfo);
            this.memoryProperties = memoryProperties;
            this.graphicsFamily = indices.getGraphicsFamily();
            this.presentationFamily = indices.getPresentationFamily();
        }
    }

    private static VkDeviceCreateInfo createDeviceCreateInfo(
            final QueueIndices indices,
            final MemoryStack stack
    ) {
        final var queueCreateInfos = createQueueCreateInfos(stack, indices);
        final var ppDeviceExtensions = getDeviceExtensions(stack);
        final var pDeviceFeatures = getDeviceFeatures(stack);
        return VkDeviceCreateInfo
                .callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pQueueCreateInfos(queueCreateInfos)
                .pEnabledFeatures(pDeviceFeatures)
                .ppEnabledExtensionNames(ppDeviceExtensions);
    }

    private static VkPhysicalDeviceFeatures getDeviceFeatures(final MemoryStack stack) {
        // We currently require no additional features
        return VkPhysicalDeviceFeatures.callocStack(stack);
    }

    private static PointerBuffer getDeviceExtensions(final MemoryStack stack) {
        final var extensions = stack.mallocPointer(1);
        final var swapchainExtensionName = stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME);
        extensions.put(swapchainExtensionName);
        extensions.flip();
        return extensions;
    }

    private static VkDeviceQueueCreateInfo.Buffer createQueueCreateInfos(
            final MemoryStack stack,
            final QueueIndices indices
    ) {
        final var uniqueIndices = new TreeSet<>(List.of(indices.getGraphicsFamily(),
                                                        indices.getPresentationFamily()));

        final var pQueuePriorities = stack.mallocFloat(1);
        pQueuePriorities.put(1.0f);
        pQueuePriorities.flip();
        final var deviceQueueCreateInfos = VkDeviceQueueCreateInfo.callocStack(uniqueIndices.size(), stack);
        var i = 0;
        for (var queueFamilyIndex : uniqueIndices) {
            deviceQueueCreateInfos.get(i)
                                  .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                                  .queueFamilyIndex(queueFamilyIndex)
                                  .pQueuePriorities(pQueuePriorities);
            i++;
        }

        return deviceQueueCreateInfos;
    }

    @Override
    public void close() {
        vkDestroyDevice(this.device, null);
    }
}
