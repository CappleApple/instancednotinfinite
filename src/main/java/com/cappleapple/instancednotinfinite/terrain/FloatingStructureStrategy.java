package com.cappleapple.instancednotinfinite.terrain;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** New floating instances retain only authored blocks; legacy saved islands keep their terrain. */
public final class FloatingStructureStrategy implements TerrainEnvelopeStrategy {
    private final SurfaceIslandStrategy legacy = new SurfaceIslandStrategy();

    @Override
    public BlockState blockAt(GenerationPlan plan, MaterialPalette palette, int x, int y, int z) {
        return plan.floatingVoid() ? Blocks.AIR.defaultBlockState() : this.legacy.blockAt(plan, palette, x, y, z);
    }
}
