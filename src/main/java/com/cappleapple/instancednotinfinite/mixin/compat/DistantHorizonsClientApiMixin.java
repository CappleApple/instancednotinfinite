package com.cappleapple.instancednotinfinite.mixin.compat;

import com.cappleapple.instancednotinfinite.client.DistantHorizonsClientCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops every Distant Horizons LOD/fade pass while the active client level is temporary. */
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
public abstract class DistantHorizonsClientApiMixin {
    @Inject(
        method = {"renderLods", "renderDeferredLodsForShaders", "renderFadeOpaque", "renderFadeTransparent"},
        at = @At("HEAD"),
        cancellable = true,
        require = 0)
    private void instancednotinfinite$suppressInstanceRendering(CallbackInfo callback) {
        if (DistantHorizonsClientCompat.shouldSuppress()) callback.cancel();
    }
}
