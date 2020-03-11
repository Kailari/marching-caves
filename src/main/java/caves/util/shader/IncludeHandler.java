package caves.util.shader;

import caves.util.io.IOUtil;
import org.lwjgl.util.shaderc.ShadercIncludeResolve;
import org.lwjgl.util.shaderc.ShadercIncludeResult;
import org.lwjgl.util.shaderc.ShadercIncludeResultRelease;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.system.MemoryUtil.memUTF8;

final class IncludeHandler {
    static final class Resolver extends ShadercIncludeResolve {
        private static final int BUFFER_SIZE = 1024;

        private final ByteBuffer sourceBuffer;
        private final String classPath;

        Resolver(final ByteBuffer sourceBuffer, final String classPath) {
            this.sourceBuffer = sourceBuffer;
            this.classPath = classPath;
        }

        @Override
        public long invoke(
                final long userData,
                final long requestedSource,
                final int type,
                final long requestingSource,
                final long includeDepth
        ) {
            final var includeResult = ShadercIncludeResult.calloc();
            final var pathRelativeToCurrentFile = classPath.substring(0, classPath.lastIndexOf('/')) + '/';
            final var requestedFilePath = memUTF8(requestedSource);
            final var sourcePath = pathRelativeToCurrentFile + requestedFilePath;

            try {
                includeResult.content(IOUtil.ioResourceToByteBuffer(sourcePath, BUFFER_SIZE));
                includeResult.source_name(memUTF8(sourcePath));
                return includeResult.address();
            } catch (final IOException e) {
                throw new AssertionError("Failed to resolve include: " + sourceBuffer);
            }
        }
    }

    static final class Releaser extends ShadercIncludeResultRelease {
        public void invoke(final long userData, final long includeResult) {
            final var result = ShadercIncludeResult.create(includeResult);
            memFree(result.source_name());
            result.free();
        }
    }
}
