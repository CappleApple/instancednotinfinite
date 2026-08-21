package com.cappleapple.instancednotinfinite.mixin.compat;

import com.cappleapple.instancednotinfinite.backend.InstanceDimensionIds;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Temporary dungeon levels do not benefit from distant LOD storage. Distant Horizons 3.2.0-b
 * otherwise retains their SQLite WAL handle after a live LevelEvent.Unload, preventing the
 * guarded DELETE_PENDING cleanup from completing until process shutdown.
 */
@Mixin(targets = "com.seibel.distanthorizons.neoforge.NeoforgeServerProxy", remap = false)
public abstract class DistantHorizonsServerProxyMixin {
    @Inject(method = "serverLevelLoadEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void instancednotinfinite$skipLevelLoad(LevelEvent.Load event, CallbackInfo callback) {
        if (event.getLevel() instanceof ServerLevel level && InstanceDimensionIds.isTemporaryInstance(level.dimension())) {
            callback.cancel();
        }
    }

    @Inject(method = "serverLevelUnloadEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void instancednotinfinite$skipLevelUnload(LevelEvent.Unload event, CallbackInfo callback) {
        if (event.getLevel() instanceof ServerLevel level && InstanceDimensionIds.isTemporaryInstance(level.dimension())) {
            callback.cancel();
        }
    }

    @Inject(method = "serverChunkLoadEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void instancednotinfinite$skipChunkLoad(ChunkEvent.Load event, CallbackInfo callback) {
        if (event.getLevel() instanceof ServerLevel level && InstanceDimensionIds.isTemporaryInstance(level.dimension())) {
            callback.cancel();
        }
    }

    @Inject(method = "playerChangedDimensionEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void instancednotinfinite$skipPlayerDimensionChange(
        PlayerEvent.PlayerChangedDimensionEvent event,
        CallbackInfo callback
    ) {
        if (InstanceDimensionIds.isTemporaryInstance(event.getFrom())
            || InstanceDimensionIds.isTemporaryInstance(event.getTo())) {
            callback.cancel();
        }
    }
}
