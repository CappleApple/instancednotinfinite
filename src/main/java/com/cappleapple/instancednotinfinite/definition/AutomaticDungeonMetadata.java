package com.cappleapple.instancednotinfinite.definition;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record AutomaticDungeonMetadata(
    ResourceLocation dungeonId,
    ResourceLocation structureId,
    List<String> sources,
    int resolvedBiomeCount,
    EnvironmentType environment,
    String environmentSource,
    String environmentReason,
    String structureType,
    String terrainAdaptation,
    String generationStep,
    int horizontalPadding,
    int verticalPadding,
    int weight,
    PlacementMode placement,
    boolean variableSize
) {
    public AutomaticDungeonMetadata {
        sources = List.copyOf(sources);
    }
}
