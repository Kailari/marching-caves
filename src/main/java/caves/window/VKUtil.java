package caves.window;

import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.ShadercIncludeResolve;
import org.lwjgl.util.shaderc.ShadercIncludeResult;
import org.lwjgl.util.shaderc.ShadercIncludeResultRelease;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static org.lwjgl.BufferUtils.createByteBuffer;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.EXTDebugReport.VK_ERROR_VALIDATION_FAILED_EXT;
import static org.lwjgl.vulkan.KHRDisplaySwapchain.VK_ERROR_INCOMPATIBLE_DISPLAY_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_ERROR_NATIVE_WINDOW_IN_USE_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_ERROR_SURFACE_LOST_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.VK11.*;

public final class VKUtil {
    private VKUtil() {
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

    // TODO: Cleanup
    public static ByteBuffer glslToSpirv(
            final String classPath,
            final int vulkanStage
    ) throws IOException {
        final ByteBuffer src = ioResourceToByteBuffer(classPath, 1024);
        final long compiler = shaderc_compiler_initialize();
        final long options = shaderc_compile_options_initialize();

        shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);
        final ShadercIncludeResolve resolver = new ShadercIncludeResolve() {
            public long invoke(
                    final long userData,
                    final long requestedSource,
                    final int type,
                    final long requestingSource,
                    final long includeDepth
            ) {
                final ShadercIncludeResult res = ShadercIncludeResult.calloc();
                try {
                    final String src = classPath.substring(0, classPath.lastIndexOf('/')) + "/" + memUTF8(requestedSource);
                    res.content(ioResourceToByteBuffer(src, 1024));
                    res.source_name(memUTF8(src));
                    return res.address();
                } catch (final IOException e) {
                    throw new AssertionError("Failed to resolve include: " + src);
                }
            }
        };
        final ShadercIncludeResultRelease releaser = new ShadercIncludeResultRelease() {
            public void invoke(final long userData, final long includeResult) {
                final ShadercIncludeResult result = ShadercIncludeResult.create(includeResult);
                memFree(result.source_name());
                result.free();
            }
        };

        shaderc_compile_options_set_include_callbacks(options, resolver, releaser, 0L);
        final long res;
        try (var stack = MemoryStack.stackPush()) {
            res = shaderc_compile_into_spv(compiler, src, vulkanStageToShadercKind(vulkanStage),
                                           stack.UTF8(classPath), stack.UTF8("main"), options);
            if (res == 0L)
                throw new AssertionError("Internal error during compilation!");
        }
        if (shaderc_result_get_compilation_status(res) != shaderc_compilation_status_success) {
            throw new AssertionError("Shader compilation failed: " + shaderc_result_get_error_message(res));
        }
        final int size = (int) shaderc_result_get_length(res);
        final ByteBuffer resultBytes = createByteBuffer(size);
        resultBytes.put(shaderc_result_get_bytes(res));
        resultBytes.flip();
        shaderc_compiler_release(res);
        shaderc_compiler_release(compiler);
        releaser.free();
        resolver.free();
        return resultBytes;
    }

    // TODO: Cleanup
    public static ByteBuffer ioResourceToByteBuffer(
            final String resource,
            final int bufferSize
    ) throws IOException {
        ByteBuffer buffer;
        final var url = Thread.currentThread().getContextClassLoader().getResource(resource);
        if (url == null) {
            throw new IOException("Classpath resource not found: " + resource);
        }
        final var file = new File(url.getFile());
        if (file.isFile()) {
            try (var fileInputStream = new FileInputStream(file);
                 var fileChannel = fileInputStream.getChannel()
            ) {
                buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
            }
        } else {
            buffer = BufferUtils.createByteBuffer(bufferSize);
            try (var source = url.openStream()) {
                if (source == null) {
                    throw new FileNotFoundException(resource);
                }

                final byte[] buf = new byte[8192];
                while (true) {
                    final int bytes = source.read(buf, 0, buf.length);
                    if (bytes == -1) {
                        break;
                    }
                    if (buffer.remaining() < bytes) {
                        buffer = resizeBuffer(buffer,
                                              Math.max(buffer.capacity() * 2,
                                                       buffer.capacity() - buffer.remaining() + bytes));
                    }
                    buffer.put(buf, 0, bytes);
                }
                buffer.flip();
            }
        }
        return buffer;
    }

    private static ByteBuffer resizeBuffer(final ByteBuffer buffer, final int newCapacity) {
        final ByteBuffer newBuffer = BufferUtils.createByteBuffer(newCapacity);
        buffer.flip();
        newBuffer.put(buffer);
        return newBuffer;
    }
}
