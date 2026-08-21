package com.cappleapple.instancednotinfinite.server;

import com.cappleapple.instancednotinfinite.command.DungeonCommands;
import com.cappleapple.instancednotinfinite.content.PortalCompletionOffering;
import com.cappleapple.instancednotinfinite.definition.DungeonDefinitionRegistry;
import com.cappleapple.instancednotinfinite.instance.DungeonInstanceManager;
import com.cappleapple.instancednotinfinite.manifestation.DungeonManifestationManager;
import com.cappleapple.instancednotinfinite.network.ManifestationNetwork;
import com.cappleapple.instancednotinfinite.recipe.PortalRecipeTierReloadListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class ServerEvents {
    private ServerEvents() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(ServerEvents::addReloadListeners);
        gameBus.addListener(DungeonCommands::register);
        gameBus.addListener(ServerEvents::serverStarted);
        gameBus.addListener(ServerEvents::serverTick);
        gameBus.addListener(ServerEvents::playerTick);
        gameBus.addListener(ServerEvents::playerLogin);
        gameBus.addListener(ServerEvents::mobSpawnPositionCheck);
        gameBus.addListener(ServerEvents::serverStopping);
    }

    private static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(DungeonDefinitionRegistry.INSTANCE);
        event.addListener(new PortalRecipeTierReloadListener(
            event.getRegistryAccess(), event.getServerResources(), event.getConditionContext()));
    }

    private static void serverStarted(ServerStartedEvent event) {
        DungeonInstanceManager.start(event.getServer());
        DungeonManifestationManager manager = DungeonManifestationManager.start(event.getServer());
        ManifestationNetwork.bind(manager);
    }

    public static void serverConfigReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() != ModConfig.Type.SERVER) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // File watching can invoke this event away from the server thread.
            server.execute(() -> DungeonInstanceManager.current().ifPresent(manager -> {
                manager.rebuildCatalogue();
                com.cappleapple.instancednotinfinite.InstancedNotInfinite.LOGGER.info(
                    "Rebuilt automatic dungeon catalogue after server config reload; active snapshots were retained");
            }));
        }
    }

    private static void serverTick(ServerTickEvent.Post event) {
        DungeonInstanceManager.current().ifPresent(DungeonInstanceManager::tick);
        DungeonManifestationManager.current().ifPresent(DungeonManifestationManager::tick);
        PortalCompletionOffering.tick(event.getServer());
    }

    private static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DungeonManifestationManager.current().ifPresent(manager -> manager.tryActivatePortal(player));
        DungeonInstanceManager.current().ifPresent(manager -> manager.tryActivateReturnPortal(player));
    }

    private static void playerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonInstanceManager.current().ifPresent(manager -> manager.recoverPlayer(player));
            ManifestationNetwork.syncDungeonCatalog(player);
            DungeonManifestationManager.current().ifPresent(manager -> {
                manager.recoverPlayer(player);
                ManifestationNetwork.syncPlayer(manager, player);
            });
        }
    }

    private static void mobSpawnPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        DungeonInstanceManager.current().ifPresent(manager -> {
            if (!manager.allowsNaturalMobSpawning(event.getLevel().getLevel())) {
                event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
            }
        });
    }

    private static void serverStopping(ServerStoppingEvent event) {
        DungeonManifestationManager.current().ifPresent(DungeonManifestationManager::close);
        ManifestationNetwork.clear();
        DungeonInstanceManager.current().ifPresent(DungeonInstanceManager::close);
    }
}
