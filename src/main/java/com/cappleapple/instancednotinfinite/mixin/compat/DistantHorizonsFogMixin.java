package com.cappleapple.instancednotinfinite.mixin.compat;

import com.cappleapple.instancednotinfinite.client.DistantHorizonsClientCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps vanilla fog active when DH rendering is suppressed for a temporary instance. */
@Mixin(targets = "com.seibel.distanthorizons.common.commonMixins.MixinVanillaFogCommon_neoforge", remap = false)
public abstract class DistantHorizonsFogMixin {
    @Inject(method = "cancelFog", at = @At("HEAD"), cancellable = true, require = 0)
    private static void instancednotinfinite$keepInstanceFog(
        Camera camera,
        FogRenderer.FogMode fogMode,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (DistantHorizonsClientCompat.shouldSuppress()) callback.setReturnValue(false);
    }
}
