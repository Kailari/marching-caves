package caves.visualization.window;

import caves.visualization.util.io.BufferUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.List;
import java.util.TreeSet;

import static caves.util.profiler.Profiler.PROFILER;
import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class LogicalDevice implements AutoCloseable {
    private final VkDevice device;
    private final QueueFamilies queueFamilies;

    /**
     * Gets the active logical device.
     *
     * @return the logical device
     */
    public VkDevice getHandle() {
        return this.device;
    }

    /**
     * Gets the queue families on this device.
     *
     * @return the queue families
     */
    public QueueFamilies getQueueFamilies() {
        return this.queueFamilies;
    }

    /**
     * Selects a suitable device and graphics queue family from the physical device.
     *
     * @param physicalDevice     the physical device to use
     * @param queueFamilies      the queue families to use
     * @param requiredExtensions required device extensions
     */
    public LogicalDevice(
            final VkPhysicalDevice physicalDevice,
            final QueueFamilies queueFamilies,
            final PointerBuffer requiredExtensions
    ) {
        if (!queueFamilies.isComplete()) {
            throw new IllegalArgumentException("Tried to create a logical device with incomplete queue indices!");
        }

        try (var stack = stackPush()) {
            BufferUtil.forEachAsStringUTF8(requiredExtensions,
                                           name -> PROFILER.log("-> Enabled device extension: {}", name));

            final VkDeviceCreateInfo deviceCreateInfo = createDeviceCreateInfo(queueFamilies,
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

            this.device = new VkDevice(device, physicalDevice, deviceCreateInfo);
            this.queueFamilies = queueFamilies;
        }
    }

    private static VkDeviceCreateInfo createDeviceCreateInfo(
            final QueueFamilies indices,
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
            final QueueFamilies indices
    ) {
        final var uniqueIndices = new TreeSet<Integer>(List.of(indices.getGraphics(),
                                                               indices.getPresent(),
                                                               indices.getTransfer()));

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
