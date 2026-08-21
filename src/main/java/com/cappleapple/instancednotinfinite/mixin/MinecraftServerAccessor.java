package com.cappleapple.instancednotinfinite.mixin;

import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The only direct access to Minecraft's runtime level registry and storage handle. */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> instancednotinfinite$getLevels();

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess instancednotinfinite$getStorageSource();
}
