package com.cappleapple.instancednotinfinite.instance;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public final class SeedDerivation {
    private SeedDerivation() {
    }

    public static long derive(long serverSeed, UUID instanceId, String dungeonId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(serverSeed).array());
            digest.update(ByteBuffer.allocate(Long.BYTES * 2)
                .putLong(instanceId.getMostSignificantBits())
                .putLong(instanceId.getLeastSignificantBits())
                .array());
            digest.update(dungeonId.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest.digest()).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }
}
