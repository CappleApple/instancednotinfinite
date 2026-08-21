package com.cappleapple.instancednotinfinite.definition;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public record ResolvedDungeonDefinition(
    DungeonDefinition definition,
    ResourceLocation structureId,
    StructureKind structureKind,
    Holder<Biome> biome,
    ResourceLocation biomeId
) {
}
