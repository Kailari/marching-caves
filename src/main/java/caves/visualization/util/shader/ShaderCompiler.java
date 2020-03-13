package caves.visualization.util.shader;

import caves.visualization.util.io.IOUtil;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.lwjgl.BufferUtils.createByteBuffer;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.NVRayTracing.*;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;

public final class ShaderCompiler {
    private ShaderCompiler() {
    }

    /**
     * Loads and compiles a GLSL shader into to SPIR-V format and returns it as a {@link
     * ByteBuffer}.
     *
     * @param classPath   classpath to load the shader from
     * @param shaderStage vulkan shader stage to assign for the shader
     *
     * @return {@link ByteBuffer} containing the compiled shader
     *
     * @throws IOException if an I/O error occurs while reading the shader
     */
    public static ByteBuffer loadGLSLShader(
            final String classPath,
            final int shaderStage
    ) throws IOException {
        final var sourceBuffer = IOUtil.ioResourceToByteBuffer(classPath, 1024);
        final var compiler = shaderc_compiler_initialize();
        final var options = shaderc_compile_options_initialize();

        shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);
        try (var resolver = new IncludeHandler.Resolver(sourceBuffer, classPath);
             var releaser = new IncludeHandler.Releaser()
        ) {

            shaderc_compile_options_set_include_callbacks(options, resolver, releaser, 0L);
            final long result;
            try (var stack = MemoryStack.stackPush()) {
                result = shaderc_compile_into_spv(compiler,
                                                  sourceBuffer,
                                                  vulkanStageToShadercKind(shaderStage),
                                                  stack.UTF8(classPath),
                                                  stack.UTF8("main"),
                                                  options);
                if (result == 0L) {
                    throw new IllegalStateException("Internal error during compilation!");
                }
            }
            if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
                throw new IllegalStateException("Shader compilation failed: "
                                                        + shaderc_result_get_error_message(result));
            }

            final var size = (int) shaderc_result_get_length(result);
            final var resultBytes = createByteBuffer(size);
            final var compiledBytes = shaderc_result_get_bytes(result);
            if (compiledBytes == null) {
                throw new IllegalStateException("Something went wrong! Shaderc returned null byte buffer!");
            }

            resultBytes.put(compiledBytes);
            resultBytes.flip();
            shaderc_compiler_release(result);
            shaderc_compiler_release(compiler);

            return resultBytes;
        }
    }

    private static int vulkanStageToShadercKind(final int stage) {
        switch (stage) {
            case VK_SHADER_STAGE_VERTEX_BIT:
                return shaderc_vertex_shader;
            case VK_SHADER_STAGE_FRAGMENT_BIT:
                return shaderc_fragment_shader;
            case VK_SHADER_STAGE_RAYGEN_BIT_NV:
                return shaderc_raygen_shader;
            case VK_SHADER_STAGE_CLOSEST_HIT_BIT_NV:
                return shaderc_closesthit_shader;
            case VK_SHADER_STAGE_MISS_BIT_NV:
                return shaderc_miss_shader;
            case VK_SHADER_STAGE_ANY_HIT_BIT_NV:
                return shaderc_anyhit_shader;
            default:
                throw new IllegalArgumentException("Stage: " + stage);
        }
    }
}
