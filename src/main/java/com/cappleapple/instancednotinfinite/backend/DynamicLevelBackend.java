package com.cappleapple.instancednotinfinite.backend;

import com.cappleapple.instancednotinfinite.definition.ResolvedDungeonDefinition;
import com.cappleapple.instancednotinfinite.instance.InstanceId;
import com.cappleapple.instancednotinfinite.terrain.DungeonChunkGenerator;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Version-sensitive runtime level operations used by the higher-level lifecycle manager. */
public interface DynamicLevelBackend {
    CreatedLevel create(MinecraftServer server, InstanceId id, ResolvedDungeonDefinition definition, long seed) throws Exception;

    void unload(MinecraftServer server, ResourceKey<Level> key) throws IOException;

    boolean isLoaded(MinecraftServer server, ResourceKey<Level> key);

    Path storagePath(MinecraftServer server, ResourceKey<Level> key);

    record CreatedLevel(ServerLevel level, DungeonChunkGenerator generator, Path storagePath) {
    }
}
