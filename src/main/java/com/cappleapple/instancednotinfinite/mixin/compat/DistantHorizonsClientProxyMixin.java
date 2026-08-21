package com.cappleapple.instancednotinfinite.mixin.compat;

import com.cappleapple.instancednotinfinite.client.DistantHorizonsClientCompat;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents DH client bookkeeping from treating temporary dungeon chunks as distant terrain. */
@Mixin(targets = "com.seibel.distanthorizons.neoforge.NeoforgeClientProxy", remap = false)
public abstract class DistantHorizonsClientProxyMixin {
    @Inject(method = "rightClickBlockEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void instancednotinfinite$skipRightClick(
        PlayerInteractEvent.RightClickBlock event,
        CallbackInfo callback
    ) {
        if (DistantHorizonsClientCompat.shouldSuppress(event.getLevel())) callback.cancel();
    }

    @Inject(method = "leftClickBlockEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void instancednotinfinite$skipLeftClick(
        PlayerInteractEvent.LeftClickBlock event,
        CallbackInfo callback
    ) {
        if (DistantHorizonsClientCompat.shouldSuppress(event.getLevel())) callback.cancel();
    }

    @Inject(method = "afterLevelRenderEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void instancednotinfinite$skipAfterLevelRender(
        RenderLevelStageEvent event,
        CallbackInfo callback
    ) {
        if (DistantHorizonsClientCompat.shouldSuppress()) callback.cancel();
    }
}
