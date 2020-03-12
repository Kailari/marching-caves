package caves.window;

import caves.util.debug.DebugMessenger;
import caves.util.io.BufferUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

import static caves.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;

public final class VulkanInstance implements AutoCloseable {
    private final VkInstance instance;

    @Nullable private final DebugMessenger debugMessenger;

    /**
     * Gets the underlying vulkan instance.
     *
     * @return the instance
     */
    public VkInstance getInstance() {
        return this.instance;
    }

    /**
     * Creates a new vulkan instance with sensible default settings.
     *
     * @param requiredExtensions extensions requested by the GLFW context
     * @param requiredLayers     required validation layers in case validation is enabled
     * @param enableValidation   should the validation and debugging utilities be enabled
     */
    public VulkanInstance(
            final PointerBuffer requiredExtensions,
            final PointerBuffer requiredLayers,
            final boolean enableValidation
    ) {
        try (var stack = stackPush()) {
            final var enabledLayerNames = selectValidationLayers(stack, requiredLayers, enableValidation);
            final var enabledExtensionNames = selectExtensions(stack, requiredExtensions, enableValidation);

            if (checkExtensionSupport(stack, enabledExtensionNames)) {
                throw new IllegalStateException("One or more requested instance extensions are not available!");
            }
            if (enableValidation && checkValidationLayerSupport(stack, enabledLayerNames)) {
                throw new IllegalStateException("One or more requested validation layers are not available!");
            }

            this.debugMessenger = enableValidation ? new DebugMessenger(stack) : null;
            this.instance = createInstance(stack, enabledExtensionNames, enabledLayerNames, this.debugMessenger);
            if (this.debugMessenger != null) {
                this.debugMessenger.initialize(stack, this.instance);
            }
        }
    }

    private static VkInstance createInstance(
            final MemoryStack stack,
            final PointerBuffer enabledExtensionNames,
            final PointerBuffer enabledLayerNames,
            @Nullable final DebugMessenger debugMessenger
    ) {
        BufferUtil.forEachAsStringUTF8(enabledExtensionNames,
                                       name -> System.out.printf("Enabled instance extension: %s\n", name));
        BufferUtil.forEachAsStringUTF8(enabledLayerNames,
                                       name -> System.out.printf("Enabled validation layer: %s\n", name));

        final var debugMessengerInfo = debugMessenger != null ? debugMessenger.getCreateInfo() : null;
        final var appInfo = createAppInfo(stack);
        final var pNext = debugMessengerInfo != null ? debugMessengerInfo.address() : NULL;
        final var instanceInfo = VkInstanceCreateInfo.callocStack(stack)
                                                     .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                                                     .pApplicationInfo(appInfo)
                                                     .ppEnabledExtensionNames(enabledExtensionNames)
                                                     .ppEnabledLayerNames(enabledLayerNames)
                                                     .pNext(pNext);

        final var pInstance = stack.mallocPointer(1);
        final var error = vkCreateInstance(instanceInfo, null, pInstance);

        if (error != VK_SUCCESS) {
            throw new IllegalStateException("Creating VkInstance failed: " + translateVulkanResult(error));
        }

        return new VkInstance(pInstance.get(), instanceInfo);
    }

    private static VkApplicationInfo createAppInfo(final MemoryStack stack) {
        return VkApplicationInfo.callocStack(stack)
                                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                                .apiVersion(VK_API_VERSION_1_1)
                                .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
                                .pApplicationName(stack.UTF8("Marching Caves"))
                                .pEngineName(stack.UTF8("No Engine"))
                                .engineVersion(VK_MAKE_VERSION(1, 0, 0));
    }

    private static PointerBuffer selectValidationLayers(
            final MemoryStack stack,
            final PointerBuffer requiredLayers,
            final boolean enableValidation
    ) {
        return enableValidation ? requiredLayers : stack.mallocPointer(0);
    }

    private static boolean checkValidationLayerSupport(
            final MemoryStack stack,
            final PointerBuffer requiredLayers
    ) {
        final var pLayerCount = stack.mallocInt(1);
        vkEnumerateInstanceLayerProperties(pLayerCount, null);

        final var availableLayers = VkLayerProperties.callocStack(pLayerCount.get(0), stack);
        vkEnumerateInstanceLayerProperties(pLayerCount, availableLayers);

        return BufferUtil.filteredForEachAsStringUTF8(
                requiredLayers,
                name -> availableLayers.stream()
                                       .map(VkLayerProperties::layerNameString)
                                       .noneMatch(name::equals),
                notFound -> System.out.printf("Validation layer \"%s\" not found.", notFound));
    }

    private static PointerBuffer selectExtensions(
            final MemoryStack stack,
            final PointerBuffer requiredExtensions,
            final boolean enableValidation
    ) {
        final var validationExtensions = enableValidation
                ? new ByteBuffer[]{stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME)}
                : new ByteBuffer[0];

        final var extensions = stack.mallocPointer(requiredExtensions.remaining() + validationExtensions.length);
        extensions.put(requiredExtensions);
        for (final var validationExtension : validationExtensions) {
            extensions.put(validationExtension);
        }
        extensions.flip();

        return extensions;
    }

    private static boolean checkExtensionSupport(
            final MemoryStack stack,
            final PointerBuffer requiredExtensions
    ) {
        final var pAvailableCount = stack.mallocInt(1);
        vkEnumerateInstanceExtensionProperties((ByteBuffer) null, pAvailableCount, null);

        final var availableExtensions = VkExtensionProperties.callocStack(pAvailableCount.get(0), stack);
        vkEnumerateInstanceExtensionProperties((ByteBuffer) null, pAvailableCount, availableExtensions);

        return BufferUtil.filteredForEachAsStringUTF8(
                requiredExtensions,
                name -> availableExtensions.stream()
                                           .map(VkExtensionProperties::extensionNameString)
                                           .noneMatch(name::equals),
                notFound -> System.out.printf("Instance extension \"%s\" not found.", notFound));
    }

    @Override
    public void close() {
        if (this.debugMessenger != null) {
            this.debugMessenger.close();
        }
        vkDestroyInstance(this.instance, null);
    }
}
