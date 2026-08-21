package com.cappleapple.instancednotinfinite.mixin.compat;

import com.cappleapple.instancednotinfinite.client.JadeTargetBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives Jade a non-null trace for client-rendered portal and hologram volumes in otherwise empty air. */
@Mixin(targets = "snownee.jade.overlay.RayTracing", remap = false)
public abstract class JadeRayTracingMixin {
    @Shadow(remap = false)
    private HitResult target;

    @Inject(method = "fire", at = @At("RETURN"), require = 0, remap = false)
    private void instancednotinfinite$selectRenderedEffect(CallbackInfo callback) {
        JadeTargetBridge.refresh(Minecraft.getInstance()).ifPresent(selected -> target = new BlockHitResult(
            selected.hitLocation(), Direction.UP, selected.anchor(), false));
    }
}
