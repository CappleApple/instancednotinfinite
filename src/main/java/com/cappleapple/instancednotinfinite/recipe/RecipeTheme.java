package com.cappleapple.instancednotinfinite.recipe;

import java.util.Locale;
import java.util.Optional;

/** Broad, additive signals used to select datapack-backed portal ingredients. */
public enum RecipeTheme {
    OVERWORLD,
    NETHER,
    END,
    UNDERGROUND,
    SURFACE,
    OCEAN,
    WATER,
    CAVE,
    MOUNTAIN,
    COLD,
    HOT,
    FIRE,
    DESERT,
    JUNGLE,
    FOREST,
    SWAMP,
    SNOW,
    UNDEAD,
    MAGIC,
    ANCIENT,
    RUINS,
    INDUSTRIAL,
    COMBAT,
    FORTRESS,
    TEMPLE;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<RecipeTheme> parse(String value) {
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
