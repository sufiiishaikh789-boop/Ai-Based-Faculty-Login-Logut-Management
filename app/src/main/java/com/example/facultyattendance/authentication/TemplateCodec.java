package com.example.facultyattendance.authentication;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** float[] embedding <-> bytes. The bytes are encrypted separately before they reach the database. */
public final class TemplateCodec {
    private TemplateCodec() { }

    public static byte[] toBytes(float[] v) {
        ByteBuffer b = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : v) b.putFloat(f);
        return b.array();
    }

    public static float[] toFloats(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length % 4 != 0)
            throw new IllegalArgumentException("invalid template length");
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[bytes.length / 4];
        for (int i = 0; i < out.length; i++) out[i] = b.getFloat();
        return out;
    }
}
