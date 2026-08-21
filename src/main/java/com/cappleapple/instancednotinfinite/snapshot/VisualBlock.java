package com.cappleapple.instancednotinfinite.snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** One exposed block in snapshot-local coordinates. */
public record VisualBlock(BlockPos position, BlockState state, VisualLayer layer) {
}
