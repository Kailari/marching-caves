package caves.util.io;

import org.lwjgl.BufferUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public final class IOUtil {
    private static final int BUFFER_SIZE = 8192;

    private IOUtil() {
    }

    /**
     * Reads an IO resource into a {@link ByteBuffer}.
     *
     * @param resource   resource to fetch
     * @param bufferSize buffer size to use while reading
     *
     * @return {@link ByteBuffer} containing the bytes of the requested IO resource
     *
     * @throws IOException if an IO error occurs while opening the resource
     */
    public static ByteBuffer ioResourceToByteBuffer(
            final String resource,
            final int bufferSize
    ) throws IOException {
        final URL url = findResource(resource);
        final var file = new File(url.getFile());
        if (file.isFile()) {
            return readFileToByteBuffer(file);
        } else {
            return readFromURLToByteBuffer(resource, bufferSize, url);
        }
    }

    private static ByteBuffer readFromURLToByteBuffer(
            final String resource,
            final int bufferSize,
            final URL url
    ) throws IOException {
        var buffer = BufferUtils.createByteBuffer(bufferSize);

        try (var source = url.openStream()) {
            if (source == null) {
                throw new FileNotFoundException(resource);
            }

            final byte[] buf = new byte[BUFFER_SIZE];
            while (true) {
                final int bytes = source.read(buf, 0, buf.length);
                if (bytes == -1) {
                    break;
                }

                if (buffer.remaining() < bytes) {
                    final var newCapacity = Math.max(buffer.capacity() * 2,
                                                     buffer.capacity() - buffer.remaining() + bytes);
                    buffer = resizeBuffer(buffer,
                                          newCapacity);
                }
                buffer.put(buf, 0, bytes);
            }
            buffer.flip();
        }

        return buffer;
    }

    private static ByteBuffer readFileToByteBuffer(final File file) throws IOException {
        try (var fileInputStream = new FileInputStream(file);
             var fileChannel = fileInputStream.getChannel()
        ) {
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
        }
    }

    private static URL findResource(final String resource) throws IOException {
        final var url = Thread.currentThread().getContextClassLoader().getResource(resource);
        if (url == null) {
            throw new IOException("Classpath resource not found: " + resource);
        }
        return url;
    }

    private static ByteBuffer resizeBuffer(final ByteBuffer buffer, final int newCapacity) {
        final var newBuffer = BufferUtils.createByteBuffer(newCapacity);
        buffer.flip();
        newBuffer.put(buffer);
        return newBuffer;
    }
}
