package caves.visualization.util.debug;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT;
import org.lwjgl.vulkan.VkInstance;

import javax.annotation.Nullable;
import java.util.Objects;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK10.VK_FALSE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public final class DebugMessenger implements AutoCloseable {
    private boolean initialized;

    @Nullable private VkDebugUtilsMessengerCreateInfoEXT createInfo;
    private VkInstance instance;
    private long debugMessenger;

    /**
     * Gets the create info for this debug messenger. Calling this is only valid before {@link
     * #initialize(MemoryStack, VkInstance) initializing} this messenger.
     *
     * @return the create info
     */
    public VkDebugUtilsMessengerCreateInfoEXT getCreateInfo() {
        if (this.createInfo == null) {
            throw new IllegalStateException("getCreateInfo called after the debug messenger was already initialized!");
        }
        return this.createInfo;
    }

    /**
     * Creates a new debug messenger for debugging instance creation/destruction. For general
     * purpose debugging, the debugger must be further {@link #initialize(MemoryStack, VkInstance)
     * initialized} after {@link VkInstance vulkan instance} has been created.
     *
     * @param stack stack to use for short-term allocations. The stack must not be popped before
     *              {@link #initialize(MemoryStack, VkInstance) initialize} is called
     */
    public DebugMessenger(final MemoryStack stack) {
        this.createInfo = VkDebugUtilsMessengerCreateInfoEXT
                .callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
                .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT
                                         | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
                                         | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
                                     | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                                     | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                .pfnUserCallback(this::messageCallback);
        this.initialized = false;
    }

    /**
     * Initializes validation layer logging for this debugger. Call this after initializing a vulkan
     * instance.
     *
     * @param stack    stack to use for short-lived allocations
     * @param instance vulkan instance to debug
     */
    public void initialize(final MemoryStack stack, final VkInstance instance) {
        Objects.requireNonNull(this.createInfo, "Create info was null while initializing debug messenger!");
        if (this.initialized) {
            throw new IllegalStateException("Debug messenger already initialized!");
        }

        this.instance = instance;

        final var pMessenger = stack.mallocLong(1);
        final var error = vkCreateDebugUtilsMessengerEXT(this.instance, this.createInfo, null, pMessenger);
        if (error != VK_SUCCESS) {
            throw new IllegalStateException("Setting up debug messenger failed: "
                                                    + translateVulkanResult(error));
        }

        this.debugMessenger = pMessenger.get(0);
        this.createInfo = null;
        this.initialized = true;
    }

    private int messageCallback(
            final int messageSeverity,
            final int messageTypes,
            final long pCallbackData,
            final long pUserData
    ) {
        final var callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
        System.out.printf("Validation layer: %s\n", callbackData.pMessageString());
        return VK_FALSE;
    }

    @Override
    public void close() {
        if (this.initialized) {
            vkDestroyDebugUtilsMessengerEXT(this.instance, this.debugMessenger, null);
        }
    }
}
