package com.cappleapple.instancednotinfinite.terrain;

import net.minecraft.world.level.block.state.BlockState;

/** Computes one block in a finite terrain envelope. Returning air leaves void or carved space. */
@FunctionalInterface
public interface TerrainEnvelopeStrategy {
    BlockState blockAt(GenerationPlan plan, MaterialPalette palette, int x, int y, int z);
}
