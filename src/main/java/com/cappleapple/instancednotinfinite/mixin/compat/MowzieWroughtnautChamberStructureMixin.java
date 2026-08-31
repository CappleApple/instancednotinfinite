package com.cappleapple.instancednotinfinite.mixin.compat;

import com.cappleapple.instancednotinfinite.structure.ControlledStructureStartGeneration;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies the chamber anchor that Mowzie normally discovers by scanning a natural cave. */
@Pseudo
@Mixin(
    targets = "com.bobmowzie.mowziesmobs.server.world.feature.structure.WroughtnautChamberStructure",
    remap = false
)
public abstract class MowzieWroughtnautChamberStructureMixin {
    @Inject(
        method = "tryWroughtChamber(Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/LevelHeightAccessor;IIILnet/minecraft/world/level/levelgen/RandomState;)Lorg/apache/commons/lang3/tuple/Pair;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void instancednotinfinite$supplyControlledCaveAnchor(
        ChunkGenerator generator,
        LevelHeightAccessor heightAccessor,
        int x,
        int surfaceHeight,
        int z,
        RandomState randomState,
        CallbackInfoReturnable<Pair<BlockPos, Rotation>> callback
    ) {
        if (!ControlledStructureStartGeneration.isActive()) return;
        int minimumY = heightAccessor.getMinBuildHeight() + 32;
        int maximumY = heightAccessor.getMaxBuildHeight() - 32;
        int chamberY = Mth.clamp(surfaceHeight - 32, minimumY, maximumY);
        callback.setReturnValue(Pair.of(new BlockPos(x, chamberY, z), Rotation.NONE));
    }
}
