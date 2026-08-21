package com.cappleapple.instancednotinfinite.definition;

import java.util.List;

public record DungeonDefinition(
    String id,
    int formatVersion,
    String structure,
    StructureKind structureKind,
    int weight,
    List<BiomeRule> biomes,
    HeightContext height,
    EnvironmentType environment,
    String customEnvironment,
    TerrainSettings terrain,
    PortalSettings portal,
    EntryPoint entry,
    PlacementMode placement,
    DecorationMode decoration,
    boolean allowNaturalMobSpawning,
    ReentryPolicy reentry
) {
    public DungeonDefinition {
        if (!DefinitionParser.isResourceId(id)) {
            throw new IllegalArgumentException("invalid dungeon id: " + id);
        }
        if (formatVersion != 1) {
            throw new IllegalArgumentException("unsupported formatVersion " + formatVersion + "; expected 1");
        }
        if (!DefinitionParser.isResourceId(structure)) {
            throw new IllegalArgumentException("invalid structure id: " + structure);
        }
        if (weight < 1) {
            throw new IllegalArgumentException("weight must be at least 1");
        }
        biomes = List.copyOf(biomes);
        if (biomes.isEmpty()) {
            throw new IllegalArgumentException("biomes must contain at least one rule");
        }
        if (environment == EnvironmentType.CUSTOM
            && (customEnvironment == null || !DefinitionParser.isResourceId(customEnvironment))) {
            throw new IllegalArgumentException("CUSTOM environment requires a valid environment.customStrategy id");
        }
        if (environment != EnvironmentType.CUSTOM && customEnvironment != null) {
            throw new IllegalArgumentException("environment.customStrategy is only valid for CUSTOM");
        }
        if (portal == null) {
            throw new IllegalArgumentException("portal settings must not be null");
        }
    }
}
