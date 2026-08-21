package com.cappleapple.instancednotinfinite.definition;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonOverrideParserTest {
    @Test
    void parsesPartialOverrideWithoutRequiringOtherFields() {
        DungeonOverrideParser.Result result = DungeonOverrideParser.parse(List.of(
            "minecraft:ancient_city;weight=3;environment=CAVE;horizontal_padding=96"));
        DungeonOverride override = result.overrides().get("minecraft:ancient_city");

        assertEquals(3, override.weight());
        assertEquals(EnvironmentType.CAVE, override.environment());
        assertEquals(96, override.horizontalPadding());
        assertNull(override.verticalPadding());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void parsesCustomStrategyResourceId() {
        DungeonOverride override = DungeonOverrideParser.parse(List.of(
            "example:crypt;environment=CUSTOM;custom_strategy=example:crypt_terrain"))
            .overrides().get("example:crypt");
        assertEquals(EnvironmentType.CUSTOM, override.environment());
        assertEquals("example:crypt_terrain", override.customStrategy());
    }

    @Test
    void parsesBiomeIdsAndTags() {
        DungeonOverride override = DungeonOverrideParser.parse(List.of(
            "example:crypt;biomes=minecraft:plains,#minecraft:is_forest"))
            .overrides().get("example:crypt");
        assertEquals(2, override.biomes().size());
        assertTrue(override.biomes().get(1).tag());
    }

    @Test
    void invalidEntryDoesNotSuppressValidOverrides() {
        DungeonOverrideParser.Result result = DungeonOverrideParser.parse(List.of(
            "broken", "example:crypt;weight=2"));
        assertEquals(1, result.overrides().size());
        assertEquals(1, result.diagnostics().size());
    }

    @Test
    void parsesTierAndPartialRecipeOverrides() {
        DungeonOverride override = DungeonOverrideParser.parse(List.of(
            "cataclysm:burning_arena;cost_tier=instancednotinfinite:epic;recipe_theme=#forge:dusts/blaze"))
            .overrides().get("cataclysm:burning_arena");

        assertEquals("instancednotinfinite:epic", override.costTier());
        assertEquals("#forge:dusts/blaze", override.recipeTheme());
        assertNull(override.recipeSignature());
    }
}
