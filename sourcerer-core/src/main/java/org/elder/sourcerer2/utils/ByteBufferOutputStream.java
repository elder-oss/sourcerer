package org.elder.sourcerer2.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class ByteBufferOutputStream extends OutputStream {
    private final ByteBuffer byteBuffer;

    public ByteBufferOutputStream(final ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    @Override
    public void write(final int b) throws IOException {
        byteBuffer.put((byte) b);
    }

    @Override
    public void write(final byte[] src) throws IOException {
        byteBuffer.put(src);
    }

    @Override
    public void write(final byte[] src, final int off, final int len) throws IOException {
        byteBuffer.put(src, off, len);
    }
}
