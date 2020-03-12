package caves.window;

import caves.util.io.BufferUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.List;
import java.util.TreeSet;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class DeviceAndQueueFamilies implements AutoCloseable {
    private final VkDevice device;

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
     * @param physicalDevice     the physical device to use
     * @param indices            queue indices to use
     * @param requiredExtensions required device extensions
     */
    public DeviceAndQueueFamilies(
            final VkPhysicalDevice physicalDevice,
            final QueueIndices indices,
            final PointerBuffer requiredExtensions
    ) {
        if (!indices.isComplete()) {
            throw new IllegalArgumentException("Tried to create a logical device with incomplete queue indices!");
        }

        try (var stack = stackPush()) {
            BufferUtil.forEachAsStringUTF8(requiredExtensions,
                                           name -> System.out.printf("Enabled device extension: %s\n", name));

            final VkDeviceCreateInfo deviceCreateInfo = createDeviceCreateInfo(indices,
                                                                               requiredExtensions,
                                                                               stack);
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

            // XXX: We do not use memory properties for anything at the moment
            // this.memoryProperties = VkPhysicalDeviceMemoryProperties.callocStack(stack);
            // vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);

            this.device = new VkDevice(device, physicalDevice, deviceCreateInfo);
            this.graphicsFamily = indices.getGraphicsFamily();
            this.presentationFamily = indices.getPresentationFamily();
        }
    }

    private static VkDeviceCreateInfo createDeviceCreateInfo(
            final QueueIndices indices,
            final PointerBuffer deviceExtensions,
            final MemoryStack stack
    ) {
        final var queueCreateInfos = createQueueCreateInfos(stack, indices);
        final var pDeviceFeatures = getDeviceFeatures(stack);
        return VkDeviceCreateInfo
                .callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pQueueCreateInfos(queueCreateInfos)
                .pEnabledFeatures(pDeviceFeatures)
                .ppEnabledExtensionNames(deviceExtensions);
    }

    private static VkPhysicalDeviceFeatures getDeviceFeatures(final MemoryStack stack) {
        // We currently require no additional features
        return VkPhysicalDeviceFeatures.callocStack(stack);
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
        for (final var queueFamilyIndex : uniqueIndices) {
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
