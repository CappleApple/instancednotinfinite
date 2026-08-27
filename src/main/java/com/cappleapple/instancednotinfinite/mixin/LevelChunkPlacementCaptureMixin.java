package com.cappleapple.instancednotinfinite.mixin;

import com.cappleapple.instancednotinfinite.structure.FloatingTerrainRemoval;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkPlacementCaptureMixin {
    @Shadow @Final private Level level;

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void instancednotinfinite$capturePlacement(BlockPos pos, BlockState state, boolean moving,
        CallbackInfoReturnable<BlockState> callback) {
        FloatingTerrainRemoval.record(this.level, pos);
    }
}
