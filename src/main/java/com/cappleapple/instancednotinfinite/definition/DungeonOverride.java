package com.cappleapple.instancednotinfinite.definition;

import java.util.List;

public record DungeonOverride(
    EnvironmentType environment,
    String customStrategy,
    List<BiomeRule> biomes,
    Integer horizontalPadding,
    Integer verticalPadding,
    Integer maximumRadius,
    Integer weight,
    PlacementMode placement,
    Boolean allowNaturalMobSpawning,
    ReentryPolicy reentry,
    String costTier,
    String recipeSignature,
    String recipeTheme,
    String recipeCore,
    String recipeCatalyst
) {
    public DungeonOverride {
        biomes = biomes == null ? null : List.copyOf(biomes);
    }
}
