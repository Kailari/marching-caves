package caves.window;

import org.lwjgl.PointerBuffer;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;

import java.nio.ByteBuffer;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.vulkan.EXTDebugReport.VK_EXT_DEBUG_REPORT_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;

public class VulkanInstance {
    private final VkInstance vkInstance;

    /**
     * Creates a new vulkan instance with sensible default settings.
     *
     * @param requiredExtensions extensions requested by the GLFW context
     * @param validationLayers   enabled validation layers
     */
    public VulkanInstance(
            final PointerBuffer requiredExtensions,
            final ByteBuffer[] validationLayers
    ) {
        try (var stack = stackPush()) {
            final var appInfo = VkApplicationInfo.callocStack(stack)
                                                 .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                                                 .apiVersion(VK_API_VERSION_1_1);

            // Get list of required extensions
            final var ppEnabledExtensionNames = stack.mallocPointer(requiredExtensions.remaining() + 1);
            final var debugReportExtension = memUTF8(VK_EXT_DEBUG_REPORT_EXTENSION_NAME);
            ppEnabledExtensionNames.put(requiredExtensions);
            ppEnabledExtensionNames.put(debugReportExtension);
            ppEnabledExtensionNames.flip();

            // Construct list of enabled validation layers names
            final var ppEnabledLayerNames = stack.mallocPointer(validationLayers.length);
            for (final ByteBuffer validationLayer : validationLayers) {
                ppEnabledLayerNames.put(validationLayer);
            }
            ppEnabledLayerNames.flip();

            // Construct instance creation info for creating the actual instance
            final var pCreateInfo = VkInstanceCreateInfo.callocStack(stack)
                                                        .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                                                        .pApplicationInfo(appInfo)
                                                        .ppEnabledExtensionNames(ppEnabledExtensionNames)
                                                        .ppEnabledLayerNames(ppEnabledLayerNames);

            // Create a vulkan instance with the default allocator. Passing `null` as the second
            // parameter enables the default memory allocator. Store a pointer to the instance in
            // pInstance.
            final var pInstance = stack.mallocPointer(1);
            final var error = vkCreateInstance(pCreateInfo, null, pInstance);

            if (error != VK_SUCCESS) {
                this.vkInstance = null;
                throw new IllegalStateException("Creating VkInstance failed: " + translateVulkanResult(error));
            }

            this.vkInstance = new VkInstance(pInstance.get(), pCreateInfo);

            memFree(appInfo.pApplicationName());
            memFree(appInfo.pEngineName());
        }
    }

    /**
     * Gets the underlying vulkan instance.
     *
     * @return the instance
     */
    public VkInstance getVkInstance() {
        return vkInstance;
    }
}
