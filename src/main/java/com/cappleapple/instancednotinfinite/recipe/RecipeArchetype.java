package com.cappleapple.instancednotinfinite.recipe;

import java.util.Locale;
import java.util.Optional;

public enum RecipeArchetype {
    BOSS,
    ARENA,
    CRYPT,
    FORTRESS,
    TEMPLE,
    MINE,
    RUIN,
    SETTLEMENT,
    INDUSTRIAL;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<RecipeArchetype> parse(String value) {
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
