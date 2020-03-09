package caves.window;

import org.lwjgl.vulkan.VkDebugReportCallbackCreateInfoEXT;
import org.lwjgl.vulkan.VkDebugReportCallbackEXT;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.EXTDebugReport.*;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public final class VulkanDebug implements AutoCloseable {
    private final long callbackHandle;
    private final VulkanInstance instance;

    /**
     * Sets up debug printing for the vulkan instance.
     *
     * @param instance instance to debug
     */
    public VulkanDebug(final VulkanInstance instance) {
        this.instance = instance;

        final var flags = VK_DEBUG_REPORT_ERROR_BIT_EXT | VK_DEBUG_REPORT_WARNING_BIT_EXT;
        final var debugCallback = new VkDebugReportCallbackEXT() {
            public int invoke(
                    final int flags,
                    final int objectType,
                    final long object,
                    final long location,
                    final int messageCode,
                    final long pLayerPrefix,
                    final long pMessage,
                    final long pUserData
            ) {
                System.err.println("ERROR OCCURRED: " + VkDebugReportCallbackEXT.getString(pMessage));
                return 0;
            }
        };

        try (var stack = stackPush()) {
            final var dbgCreateInfo = VkDebugReportCallbackCreateInfoEXT
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DEBUG_REPORT_CREATE_INFO_EXT)
                    .pfnCallback(debugCallback)
                    .flags(flags);

            final var pCallback = stack.mallocLong(1);
            final var error = vkCreateDebugReportCallbackEXT(instance.getVkInstance(),
                                                             dbgCreateInfo,
                                                             null,
                                                             pCallback);
            if (error != VK_SUCCESS) {
                throw new AssertionError("Failed to setup VkInstance debugging: "
                                                 + translateVulkanResult(error));
            }
            this.callbackHandle = pCallback.get();
        }
    }

    @Override
    public void close() {
        vkDestroyDebugReportCallbackEXT(this.instance.getVkInstance(),
                                        this.callbackHandle,
                                        null);
    }
}
