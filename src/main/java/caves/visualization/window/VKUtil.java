package caves.visualization.window;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.vulkan.EXTDebugReport.VK_ERROR_VALIDATION_FAILED_EXT;
import static org.lwjgl.vulkan.KHRDisplaySwapchain.VK_ERROR_INCOMPATIBLE_DISPLAY_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_ERROR_NATIVE_WINDOW_IN_USE_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_ERROR_SURFACE_LOST_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.VK11.*;

public final class VKUtil {
    private static final ByteBuffer ENTRY_POINT_NAME = memUTF8("main");

    private VKUtil() {
    }

    /**
     * Loads a shader module by reading its source from file and creates a stage from it. Shader is
     * loaded on the thread-local stack.
     *
     * @param device     device to use for the shader
     * @param shaderCode compiled shader code
     * @param stage      shader stage to load the shader as
     *
     * @return shader stage create info for the given shader
     */
    public static VkPipelineShaderStageCreateInfo loadShader(
            final VkDevice device,
            final ByteBuffer shaderCode,
            final int stage
    ) {
        return VkPipelineShaderStageCreateInfo.callocStack()
                                              .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                                              .stage(stage)
                                              .module(createShaderModule(shaderCode, device))
                                              .pName(ENTRY_POINT_NAME);
    }

    private static long createShaderModule(
            final ByteBuffer shaderCode,
            final VkDevice device
    ) {
        try (var stack = stackPush()) {
            final VkShaderModuleCreateInfo moduleCreateInfo = VkShaderModuleCreateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(shaderCode);

            final var pShaderModule = stack.mallocLong(1);
            final var result = vkCreateShaderModule(device, moduleCreateInfo, null, pShaderModule);
            if (result != VK_SUCCESS) {
                throw new AssertionError("Failed to create shader module: "
                                                 + translateVulkanResult(result));
            }

            return pShaderModule.get(0);
        }
    }

    /**
     * Translates the given vulkan result code into a human-readable message. Messages as per LWJGL3
     * vulkan examples `VKUtil.java`
     *
     * @param result result code to translate
     *
     * @return human-readable message corresponding to the given result code
     */
    public static String translateVulkanResult(final int result) {
        switch (result) {
            // Success codes
            case VK_SUCCESS:
                return "Command successfully completed.";
            case VK_NOT_READY:
                return "A fence or query has not yet completed.";
            case VK_TIMEOUT:
                return "A wait operation has not completed in the specified time.";
            case VK_EVENT_SET:
                return "An event is signaled.";
            case VK_EVENT_RESET:
                return "An event is unsignaled.";
            case VK_INCOMPLETE:
                return "A return array was too small for the result.";
            case VK_SUBOPTIMAL_KHR:
                return "A swapchain no longer matches the surface properties exactly, but can "
                        + "still be used to present to the surface successfully.";

            // Error codes
            case VK_ERROR_OUT_OF_HOST_MEMORY:
                return "A host memory allocation has failed.";
            case VK_ERROR_OUT_OF_DEVICE_MEMORY:
                return "A device memory allocation has failed.";
            case VK_ERROR_INITIALIZATION_FAILED:
                return "Initialization of an object could not be completed for implementation"
                        + "-specific reasons.";
            case VK_ERROR_DEVICE_LOST:
                return "The logical or physical device has been lost.";
            case VK_ERROR_MEMORY_MAP_FAILED:
                return "Mapping of a memory object has failed.";
            case VK_ERROR_LAYER_NOT_PRESENT:
                return "A requested layer is not present or could not be loaded.";
            case VK_ERROR_EXTENSION_NOT_PRESENT:
                return "A requested extension is not supported.";
            case VK_ERROR_FEATURE_NOT_PRESENT:
                return "A requested feature is not supported.";
            case VK_ERROR_INCOMPATIBLE_DRIVER:
                return "The requested version of Vulkan is not supported by the driver or is"
                        + " otherwise incompatible for implementation-specific reasons.";
            case VK_ERROR_TOO_MANY_OBJECTS:
                return "Too many objects of the type have already been created.";
            case VK_ERROR_FORMAT_NOT_SUPPORTED:
                return "A requested format is not supported on this device.";
            case VK_ERROR_SURFACE_LOST_KHR:
                return "A surface is no longer available.";
            case VK_ERROR_NATIVE_WINDOW_IN_USE_KHR:
                return "The requested window is already connected to a VkSurfaceKHR, or to some "
                        + "other non-Vulkan API.";
            case VK_ERROR_OUT_OF_DATE_KHR:
                return "A surface has changed in such a way that it is no longer compatible with "
                        + "the swapchain, and further presentation requests using the swapchain "
                        + "will fail. Applications must query the new surface properties and "
                        + "recreate their swapchain if they wish to continue presenting to "
                        + "the surface.";
            case VK_ERROR_INCOMPATIBLE_DISPLAY_KHR:
                return "The display used by a swapchain does not use the same presentable image "
                        + "layout, or is incompatible in a way that prevents sharing an image.";
            case VK_ERROR_VALIDATION_FAILED_EXT:
                return "A validation layer found an error.";
            default:
                return String.format("%s [%d]", "Unknown", result);
        }
    }
}
