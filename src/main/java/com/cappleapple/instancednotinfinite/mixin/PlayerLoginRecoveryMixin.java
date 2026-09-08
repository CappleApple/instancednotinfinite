package com.cappleapple.instancednotinfinite.mixin;

import com.cappleapple.instancednotinfinite.player.PlayerReturnManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs before vanilla position loading and Sable's LoginPoint restoration. */
@Mixin(value = Entity.class, priority = 1500)
public abstract class PlayerLoginRecoveryMixin {
    @Inject(method = "load", at = @At("HEAD"))
    private void instancednotinfinite$recoverBeforeLoad(CompoundTag tag, CallbackInfo callback) {
        if ((Object)this instanceof ServerPlayer player && player.connection == null) {
            PlayerReturnManager.recoverBeforeLoad(player, tag);
        }
    }
}
