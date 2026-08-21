package com.cappleapple.instancednotinfinite.recipe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public final class DeterministicIngredientSelector {
    private DeterministicIngredientSelector() {
    }

    public static ResourceLocation select(ResourceLocation structureId, String role, List<ResourceLocation> candidates) {
        if (candidates.isEmpty()) throw new IllegalArgumentException("Cannot select from an empty ingredient pool");
        List<ResourceLocation> ordered = candidates.stream().distinct().sorted(Comparator.naturalOrder()).toList();
        return ordered.get(Math.floorMod(stableHash(structureId + "\u0000" + role), ordered.size()));
    }

    public static String selectString(String structureId, String role, List<String> candidates) {
        if (candidates.isEmpty()) throw new IllegalArgumentException("Cannot select from an empty ingredient pool");
        List<String> ordered = candidates.stream().distinct().sorted().toList();
        return ordered.get(Math.floorMod(stableHash(structureId + "\u0000" + role), ordered.size()));
    }

    static int stableHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return (digest[0] & 0xff) << 24 | (digest[1] & 0xff) << 16 | (digest[2] & 0xff) << 8 | digest[3] & 0xff;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
