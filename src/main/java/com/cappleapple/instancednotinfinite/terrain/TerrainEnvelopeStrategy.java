package com.cappleapple.instancednotinfinite.terrain;

import net.minecraft.world.level.block.state.BlockState;

/** Computes one block in a finite terrain envelope. Returning air leaves void or carved space. */
@FunctionalInterface
public interface TerrainEnvelopeStrategy {
    BlockState blockAt(GenerationPlan plan, MaterialPalette palette, int x, int y, int z);

    /** Precompute column geometry once, rather than repeating noise sampling at every height. */
    default java.util.function.IntFunction<BlockState> column(GenerationPlan plan, MaterialPalette palette, int x, int z) {
        return y -> blockAt(plan, palette, x, y, z);
    }
}
