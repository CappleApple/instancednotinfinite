package com.cappleapple.instancednotinfinite.mixin.compat;

import com.cappleapple.instancednotinfinite.compat.LoadedEntityQuery;
import com.cappleapple.instancednotinfinite.player.PlayerReturnManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve minion cleanup without the enormous spatial query rejected by Sable. */
@Pseudo
@Mixin(targets = "net.bandit.darkdoppelganger.event.ServerEvents", remap = false)
public abstract class DarkDoppelgangerUnloadMixin {
    @WrapOperation(method = {"onWorldUnload", "lambda$onServerStopping$5"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
        require = 0)
    private static <T extends Entity> List<T> instancednotinfinite$loadedMinionsOnly(
            ServerLevel level, Class<T> type, AABB bounds, Predicate<? super T> predicate, Operation<List<T>> original) {
        return PlayerReturnManager.isInstanceDimension(level.dimension().location())
            || net.neoforged.fml.ModList.get().isLoaded("sable")
            ? LoadedEntityQuery.matching(level, type, bounds, predicate)
            : original.call(level, type, bounds, predicate);
    }
}
