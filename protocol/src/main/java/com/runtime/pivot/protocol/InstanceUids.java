package com.runtime.pivot.protocol;

import com.google.protobuf.ByteString;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class InstanceUids {
    private InstanceUids() {
    }

    public static ByteString randomV7() {
        return ByteString.copyFrom(uuidToBytes(randomUuidV7()));
    }

    public static UUID randomUuidV7() {
        long millis = System.currentTimeMillis();
        long msb = (millis << 16) & 0xFFFFFFFFFFFF0000L;
        msb |= 0x7000L;
        msb |= ThreadLocalRandom.current().nextLong() & 0x0FFFL;
        long lsb = ThreadLocalRandom.current().nextLong();
        lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }

    public static UUID fromBytes(ByteString bytes) {
        if (bytes == null || bytes.size() != 16) {
            throw new IllegalArgumentException("instance_uid must be 16 bytes");
        }
        ByteBuffer buffer = bytes.asReadOnlyByteBuffer();
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    public static byte[] uuidToBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return bytes;
    }
}
