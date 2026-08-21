package com.cappleapple.instancednotinfinite.api;

import com.cappleapple.instancednotinfinite.instance.DungeonInstance;
import com.cappleapple.instancednotinfinite.instance.DungeonInstanceManager;
import com.cappleapple.instancednotinfinite.instance.InstanceId;
import com.cappleapple.instancednotinfinite.instance.InstanceOperationException;
import com.cappleapple.instancednotinfinite.terrain.CustomTerrainStrategies;
import com.cappleapple.instancednotinfinite.terrain.TerrainEnvelopeStrategy;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Stable entry point for blocks, quests, scripting addons, and other mods. */
public final class InstancedDungeons {
    private InstancedDungeons() {
    }

    /** Creates the requested dungeon without moving a player. Call on the server thread. */
    public static InstanceView createDungeon(MinecraftServer server, ResourceLocation dungeonId) throws InstanceOperationException {
        return InstanceView.from(DungeonInstanceManager.get(server).create(dungeonId));
    }

    /** Selects a loaded definition by weight and creates it without moving a player. */
    public static InstanceView createRandomDungeon(MinecraftServer server) throws InstanceOperationException {
        return InstanceView.from(DungeonInstanceManager.get(server).createRandom());
    }

    /** Creates the requested dungeon and stores/teleports the initiating player. */
    public static InstanceView createAndEnter(ServerPlayer player, ResourceLocation dungeonId) throws InstanceOperationException {
        return InstanceView.from(DungeonInstanceManager.get(player.getServer()).createAndEnter(dungeonId, player));
    }

    /** Selects a definition by weight, creates it, and stores/teleports the player. */
    public static InstanceView createRandomAndEnter(ServerPlayer player) throws InstanceOperationException {
        return InstanceView.from(DungeonInstanceManager.get(player.getServer()).createRandomAndEnter(player));
    }

    /** Adds an eligible player to an existing instance and teleports them to its safe entry. */
    public static void joinDungeon(ServerPlayer player, UUID instanceId) throws InstanceOperationException {
        DungeonInstanceManager.get(player.getServer()).enter(player, new InstanceId(instanceId));
    }

    /** Returns a player currently inside an instance to their persisted origin or fallback. */
    public static boolean leaveDungeon(ServerPlayer player) {
        return DungeonInstanceManager.get(player.getServer()).leave(player);
    }

    /** Marks an instance complete; its configured exit delay then drives return and cleanup. */
    public static void completeDungeon(MinecraftServer server, UUID instanceId) throws InstanceOperationException {
        DungeonInstanceManager.get(server).complete(new InstanceId(instanceId));
    }

    /** Returns an immutable public view of an instance when its record exists. */
    public static Optional<InstanceView> getInstance(MinecraftServer server, UUID instanceId) {
        return DungeonInstanceManager.get(server).get(new InstanceId(instanceId)).map(InstanceView::from);
    }

    /** Finds the runtime or assigned instance associated with a player. */
    public static Optional<InstanceView> getPlayerInstance(ServerPlayer player) {
        return DungeonInstanceManager.get(player.getServer()).getPlayerInstance(player).map(InstanceView::from);
    }

    /** Register during mod initialization before a CUSTOM dungeon is created. */
    public static void registerTerrainStrategy(ResourceLocation id, TerrainEnvelopeStrategy strategy) {
        CustomTerrainStrategies.register(id, strategy);
    }

    /** Convenience alias for the dedicated manifestation API. */
    public static ManifestationView spawnManifestation(
        ServerLevel level,
        BlockPos origin,
        ResourceLocation dungeonId,
        Direction orientation,
        ServerPlayer initiator
    ) throws InstanceOperationException {
        return DungeonManifestationApi.spawn(
            level, origin,
            com.cappleapple.instancednotinfinite.manifestation.DungeonTarget.dungeon(dungeonId),
            com.cappleapple.instancednotinfinite.manifestation.ManifestationOptions.defaults(orientation),
            initiator);
    }
}
