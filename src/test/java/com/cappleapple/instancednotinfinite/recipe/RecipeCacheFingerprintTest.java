package com.cappleapple.instancednotinfinite.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecipeCacheFingerprintTest {
    @Test
    void equivalentInputsArePortableAndOrderIndependent() {
        String first = RecipeCacheFingerprint.digest(List.of(
            "dungeon=minecraft:igloo", "paletteInference=true", "excludedBlock=minecraft:bedrock"));
        String second = RecipeCacheFingerprint.digest(List.of(
            "excludedBlock=minecraft:bedrock", "dungeon=minecraft:igloo", "paletteInference=true"));

        assertEquals(first, second);
        assertEquals(64, first.length());
    }

    @Test
    void structureOrInferenceChangesInvalidateTheKey() {
        String baseline = RecipeCacheFingerprint.digest(List.of(
            "dungeon=minecraft:igloo", "paletteInference=true"));

        assertNotEquals(baseline, RecipeCacheFingerprint.digest(List.of(
            "dungeon=minecraft:mineshaft", "paletteInference=true")));
        assertNotEquals(baseline, RecipeCacheFingerprint.digest(List.of(
            "dungeon=minecraft:igloo", "paletteInference=false")));
    }
}
