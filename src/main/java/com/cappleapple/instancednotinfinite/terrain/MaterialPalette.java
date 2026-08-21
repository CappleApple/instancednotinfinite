package com.cappleapple.instancednotinfinite.terrain;

import com.cappleapple.instancednotinfinite.definition.DungeonDefinition;
import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

public record MaterialPalette(BlockState core, BlockState filler, BlockState surface, BlockState fluid) {
    public static MaterialPalette forDefinition(DungeonDefinition definition) {
        return forDefinition(definition, null);
    }

    public static MaterialPalette forDefinition(DungeonDefinition definition, Holder<Biome> biome) {
        if (definition.environment() == EnvironmentType.NETHER_LIKE) {
            return new MaterialPalette(
                Blocks.BASALT.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(),
                Blocks.NETHERRACK.defaultBlockState(), Blocks.LAVA.defaultBlockState());
        }
        if (definition.environment() == EnvironmentType.END_LIKE) {
            return new MaterialPalette(
                Blocks.END_STONE.defaultBlockState(), Blocks.END_STONE.defaultBlockState(),
                Blocks.END_STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
        }
        if (definition.environment() == EnvironmentType.UNDERWATER
            || definition.environment() == EnvironmentType.OCEAN_SURFACE) {
            return new MaterialPalette(
                Blocks.STONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
                Blocks.SAND.defaultBlockState(), Blocks.WATER.defaultBlockState());
        }
        BlockState core = definition.height().midpoint() < 0
            ? Blocks.DEEPSLATE.defaultBlockState()
            : Blocks.STONE.defaultBlockState();
        if (definition.environment() == EnvironmentType.SURFACE || definition.environment() == EnvironmentType.FLOATING_ISLAND) {
            if (biome != null && biome.is(Tags.Biomes.IS_BADLANDS)) {
                return new MaterialPalette(
                    Blocks.TERRACOTTA.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(),
                    Blocks.RED_SAND.defaultBlockState(), Blocks.WATER.defaultBlockState());
            }
            if (biome != null && (biome.is(Tags.Biomes.IS_DESERT) || biome.is(Tags.Biomes.IS_SANDY))) {
                return new MaterialPalette(
                    core, Blocks.SANDSTONE.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.WATER.defaultBlockState());
            }
            if (biome != null && biome.is(Tags.Biomes.IS_MUSHROOM)) {
                return new MaterialPalette(
                    core, Blocks.DIRT.defaultBlockState(), Blocks.MYCELIUM.defaultBlockState(), Blocks.WATER.defaultBlockState());
            }
            if (biome != null && biome.is(Tags.Biomes.IS_STONY_SHORES)) {
                return new MaterialPalette(core, core, core, Blocks.WATER.defaultBlockState());
            }
            return new MaterialPalette(core, Blocks.DIRT.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.WATER.defaultBlockState());
        }
        return new MaterialPalette(core, core, core, Blocks.WATER.defaultBlockState());
    }
}
