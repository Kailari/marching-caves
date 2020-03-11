package caves.window;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;

public final class VulkanInstance implements AutoCloseable {
    private final VkInstance vkInstance;

    private final long debugMessenger;
    private final boolean debugEnabled;

    /**
     * Creates a new vulkan instance with sensible default settings.
     *
     * @param requiredExtensions extensions requested by the GLFW context
     * @param validationLayers   enabled validation layers
     * @param enableValidation   should the validation and debugging utilities be enabled
     */
    public VulkanInstance(
            final PointerBuffer requiredExtensions,
            final ByteBuffer[] validationLayers,
            final boolean enableValidation
    ) {
        this.debugEnabled = enableValidation;

        try (var stack = stackPush()) {
            final var appInfo = createAppInfo(stack);
            final VkInstanceCreateInfo pCreateInfo = createInstanceCreateInfo(requiredExtensions,
                                                                              validationLayers,
                                                                              stack,
                                                                              appInfo,
                                                                              enableValidation);
            this.vkInstance = createInstance(stack, pCreateInfo);
            this.debugMessenger = enableValidation ? createDebugMessenger(stack, this.vkInstance) : NULL;
            memFree(appInfo.pApplicationName());
            memFree(appInfo.pEngineName());
        }
    }

    private static long createDebugMessenger(
            final MemoryStack stack,
            final VkInstance instance
    ) {
        final var pCreateInfo = createDebugMessengerCreateInfo();
        final var pMessenger = stack.mallocLong(1);
        final var error = vkCreateDebugUtilsMessengerEXT(instance, pCreateInfo, null, pMessenger);
        if (error != VK_SUCCESS) {
            throw new IllegalStateException("Setting up debug messenger failed: "
                                                    + translateVulkanResult(error));
        }

        return pMessenger.get(0);
    }

    private static VkDebugUtilsMessengerCreateInfoEXT createDebugMessengerCreateInfo() {
        return VkDebugUtilsMessengerCreateInfoEXT
                .calloc()
                .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
                .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT
                                         | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
                                         | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
                                     | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                                     | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                .pfnUserCallback((messageSeverity, messageTypes, pCallbackData, pUserData) -> {
                    final var callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
                    System.out.printf("Validation layer: %s\n", callbackData.pMessageString());
                    return VK_FALSE;
                });
    }

    private static VkInstance createInstance(
            final MemoryStack stack,
            final VkInstanceCreateInfo pCreateInfo
    ) {
        final var pInstance = stack.mallocPointer(1);

        // Create a vulkan instance with the default allocator. Passing `null` as the second
        // parameter enables the default memory allocator. Store a pointer to the instance in
        // pInstance.
        final var error = vkCreateInstance(pCreateInfo, null, pInstance);

        if (error != VK_SUCCESS) {
            throw new IllegalStateException("Creating VkInstance failed: " + translateVulkanResult(error));
        }

        return new VkInstance(pInstance.get(), pCreateInfo);
    }

    private VkInstanceCreateInfo createInstanceCreateInfo(
            final PointerBuffer requiredExtensions,
            final ByteBuffer[] validationLayers,
            final MemoryStack stack,
            final VkApplicationInfo appInfo,
            final boolean enableValidation
    ) {
        // Construct list of enabled validation layers names
        final PointerBuffer ppEnabledLayerNames;
        final long next;
        if (enableValidation) {
            ppEnabledLayerNames = stack.mallocPointer(validationLayers.length);
            for (final ByteBuffer validationLayer : validationLayers) {
                ppEnabledLayerNames.put(validationLayer);
            }
            ppEnabledLayerNames.flip();
            next = createDebugMessengerCreateInfo().address();
        } else {
            ppEnabledLayerNames = stack.mallocPointer(0);
            next = NULL;
        }

        // Construct instance creation info for creating the actual instance
        // TODO: Validate that all required extensions are available
        if (enableValidation && checkValidationLayerSupport(stack, validationLayers)) {
            throw new IllegalStateException("One or more requested validation layers are not available!");
        }
        return VkInstanceCreateInfo.callocStack(stack)
                                   .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                                   .pApplicationInfo(appInfo)
                                   .ppEnabledExtensionNames(requiredExtensions)
                                   .ppEnabledLayerNames(ppEnabledLayerNames)
                                   .pNext(next);
    }

    private boolean checkValidationLayerSupport(
            final MemoryStack stack,
            final ByteBuffer[] validationLayers
    ) {
        final var pLayerCount = stack.mallocInt(1);
        vkEnumerateInstanceLayerProperties(pLayerCount, null);

        final var layerPropertiesBuffer = VkLayerProperties.callocStack(pLayerCount.get(0), stack);
        vkEnumerateInstanceLayerProperties(pLayerCount, layerPropertiesBuffer);

        for (var layerName : validationLayers) {
            layerName.mark();
            final var layerNameString = StandardCharsets.UTF_8.decode(layerName)
                                                              .toString()
                                                              .trim(); // HACK: .trim removes null-terminators etc.
            layerName.reset();

            var found = false;
            for (var layerProperties : layerPropertiesBuffer) {
                if (layerNameString.equals(layerProperties.layerNameString())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                System.out.printf("Validation layer \"%s\" not found.", layerNameString);
                return true;
            }
        }
        return false;
    }

    private static VkApplicationInfo createAppInfo(final MemoryStack stack) {
        return VkApplicationInfo.callocStack(stack)
                                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                                .apiVersion(VK_API_VERSION_1_1)
                                .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
                                .pApplicationName(memUTF8("Marching Caves"))
                                .pEngineName(memUTF8("No Engine"))
                                .engineVersion(VK_MAKE_VERSION(1, 0, 0));
    }

    /**
     * Gets the underlying vulkan instance.
     *
     * @return the instance
     */
    public VkInstance getVkInstance() {
        return this.vkInstance;
    }

    @Override
    public void close() {
        if (this.debugEnabled) {
            vkDestroyDebugUtilsMessengerEXT(this.vkInstance, this.debugMessenger, null);
        }
        vkDestroyInstance(this.vkInstance, null);
    }
}
