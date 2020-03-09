package caves.window;

import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;

public class DeviceAndGraphicsQueueFamily {
    private final VkDevice device;
    private final int queueFamilyIndex;
    private final VkPhysicalDeviceMemoryProperties memoryProperties;

    /**
     * Selects a suitable device and graphics queue family from the physical device.
     *
     * @param physicalDevice   the physical device to use
     * @param validationLayers enabled validation layers
     */
    public DeviceAndGraphicsQueueFamily(
            final PhysicalDevice physicalDevice,
            final ByteBuffer[] validationLayers
    ) {
        try (var stack = stackPush()) {
            final var pQueueFamilyPropertyCount = stack.mallocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice.getVkPhysicalDevice(),
                                                     pQueueFamilyPropertyCount,
                                                     null);

            final var queueCount = pQueueFamilyPropertyCount.get(0);
            final var queueProps = VkQueueFamilyProperties.callocStack(queueCount, stack);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice.getVkPhysicalDevice(),
                                                     pQueueFamilyPropertyCount,
                                                     queueProps);

            int selected;
            for (selected = 0; selected < queueCount; ++selected) {
                if ((queueProps.get(selected).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    break;
                }
            }
            final var graphicsQueueFamilyIndex = selected;

            final var pQueuePriorities = stack.mallocFloat(1).put(0.0f);
            pQueuePriorities.flip();
            VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo
                    .callocStack(1, stack)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                    .queueFamilyIndex(graphicsQueueFamilyIndex)
                    .pQueuePriorities(pQueuePriorities);

            final var extensions = stack.mallocPointer(1);
            final var swapchainExtensionName = memUTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME);
            extensions.put(swapchainExtensionName);
            extensions.flip();

            final var ppEnabledLayerNames = stack.mallocPointer(validationLayers.length);
            for (final ByteBuffer validationLayer : validationLayers) {
                ppEnabledLayerNames.put(validationLayer);
            }
            ppEnabledLayerNames.flip();

            final var deviceCreateInfo = VkDeviceCreateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    .pQueueCreateInfos(queueCreateInfos)
                    .ppEnabledExtensionNames(extensions)
                    .ppEnabledLayerNames(ppEnabledLayerNames);

            final var pDevice = stack.mallocPointer(1);
            final var error = vkCreateDevice(physicalDevice.getVkPhysicalDevice(),
                                             deviceCreateInfo,
                                             null,
                                             pDevice);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Creating device failed: "
                                                        + translateVulkanResult(error));
            }
            final var device = pDevice.get(0);

            final var memoryProperties = VkPhysicalDeviceMemoryProperties.callocStack(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice.getVkPhysicalDevice(),
                                                memoryProperties);

            this.device = new VkDevice(device, physicalDevice.getVkPhysicalDevice(), deviceCreateInfo);
            this.queueFamilyIndex = graphicsQueueFamilyIndex;
            this.memoryProperties = memoryProperties;

            memFree(swapchainExtensionName);
        }
    }
}
