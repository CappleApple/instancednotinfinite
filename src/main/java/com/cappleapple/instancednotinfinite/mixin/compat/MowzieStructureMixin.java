package com.cappleapple.instancednotinfinite.mixin.compat;

import com.cappleapple.instancednotinfinite.compat.mowzie.MowzieStructureAccess;
import com.cappleapple.instancednotinfinite.structure.ControlledStructureStartGeneration;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets explicitly selected Mowzie structures generate without natural-world biome/terrain vetoes. */
@Pseudo
@Mixin(targets = "com.bobmowzie.mowziesmobs.server.world.feature.structure.MowzieStructure", remap = false)
public abstract class MowzieStructureMixin implements MowzieStructureAccess {
    @Shadow(remap = false)
    private Set<Holder<Biome>> allowedBiomes;

    @Override
    public Set<Holder<Biome>> instancednotinfinite$allowedBiomes() {
        return this.allowedBiomes == null ? Set.of() : Set.copyOf(this.allowedBiomes);
    }

    @Inject(
        method = "checkLocation(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void instancednotinfinite$allowControlledStart(
        Structure.GenerationContext context,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (ControlledStructureStartGeneration.isActive()) {
            callback.setReturnValue(true);
        }
    }
}
