package com.cappleapple.instancednotinfinite.recipe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

/** Stable, portable cache keys built only from canonical content rather than local paths or timestamps. */
public final class RecipeCacheFingerprint {
    private RecipeCacheFingerprint() {
    }

    public static String digest(Collection<String> inputs) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            inputs.stream().sorted().forEach(value -> {
                digest.update(value.getBytes(StandardCharsets.UTF_8));
                digest.update((byte)'\n');
            });
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
