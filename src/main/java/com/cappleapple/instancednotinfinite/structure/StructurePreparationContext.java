package com.cappleapple.instancednotinfinite.structure;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Worldgen inputs captured on the server thread, without exposing a live level to the planner. */
public record StructurePreparationContext(
    RegistryAccess registryAccess,
    StructureTemplateManager templates,
    RandomState randomState,
    int getMinBuildHeight,
    int getHeight
) implements LevelHeightAccessor {
    public static StructurePreparationContext capture(ServerLevel level) {
        return new StructurePreparationContext(level.registryAccess(), level.getStructureManager(),
            level.getChunkSource().randomState(), level.getMinBuildHeight(), level.getHeight());
    }
}
