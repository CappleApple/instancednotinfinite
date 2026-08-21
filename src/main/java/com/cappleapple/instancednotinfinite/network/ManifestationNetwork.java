package com.cappleapple.instancednotinfinite.network;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.definition.DungeonDefinitionRegistry;
import com.cappleapple.instancednotinfinite.instance.DungeonInstanceManager;
import com.cappleapple.instancednotinfinite.instance.SeedDerivation;
import com.cappleapple.instancednotinfinite.manifestation.DungeonManifestation;
import com.cappleapple.instancednotinfinite.manifestation.DungeonManifestationManager;
import com.cappleapple.instancednotinfinite.manifestation.ParticleColor;
import com.cappleapple.instancednotinfinite.manifestation.PortalAppearanceResolver;
import com.cappleapple.instancednotinfinite.manifestation.ResolvedPortalColors;
import com.cappleapple.instancednotinfinite.snapshot.DungeonVisualSnapshot;
import com.cappleapple.instancednotinfinite.snapshot.VisualBlock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class ManifestationNetwork {
    public static final String PROTOCOL_VERSION = "9";
    private static final int BLOCKS_PER_PACKET = 2048;
    private static final Map<UUID, Set<UUID>> KNOWN = new HashMap<>();

    private ManifestationNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ManifestationNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(ManifestationStartPayload.TYPE, ManifestationStartPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> com.cappleapple.instancednotinfinite.client.ClientPayloadHandler.handle(payload)));
        registrar.playToClient(ManifestationBlocksPayload.TYPE, ManifestationBlocksPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> com.cappleapple.instancednotinfinite.client.ClientPayloadHandler.handle(payload)));
        registrar.playToClient(ManifestationProgressPayload.TYPE, ManifestationProgressPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> com.cappleapple.instancednotinfinite.client.ClientPayloadHandler.handle(payload)));
        registrar.playToClient(ManifestationRemovePayload.TYPE, ManifestationRemovePayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> com.cappleapple.instancednotinfinite.client.ClientPayloadHandler.handle(payload)));
        registrar.playToClient(DungeonCatalogPayload.TYPE, DungeonCatalogPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> com.cappleapple.instancednotinfinite.client.ClientPayloadHandler.handle(payload)));
    }

    public static void bind(DungeonManifestationManager manager) {
        manager.setSnapshotBatchListener(ManifestationNetwork::broadcastBatch);
    }

    public static void syncNearby(DungeonManifestationManager manager) {
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(manager, player);
        }
    }

    public static void syncPlayer(DungeonManifestationManager manager, ServerPlayer player) {
        if (!canSend(player, ManifestationStartPayload.TYPE.id())) {
            KNOWN.remove(player.getUUID());
            return;
        }
        Set<UUID> known = KNOWN.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>());
        Set<UUID> nearby = new HashSet<>();
        double radiusSquared = Math.pow(ServerConfig.INSTANCE.manifestationRenderDistance.get(), 2);
        for (DungeonManifestation value : manager.values()) {
            boolean inRange = value.originDimension().equals(player.level().dimension().location())
                && value.origin().distToCenterSqr(player.position()) <= radiusSquared
                && !value.state().terminal();
            if (!inRange) continue;
            nearby.add(value.id());
            if (known.add(value.id())) sendFull(manager, player, value);
        }
        for (UUID id : Set.copyOf(known)) {
            if (!nearby.contains(id)) {
                send(player, new ManifestationRemovePayload(id));
                known.remove(id);
            }
        }
    }

    public static void syncDungeonCatalog(ServerPlayer player) {
        send(player, dungeonCatalogPayload(player.getServer()));
    }

    public static void broadcastDungeonCatalog() {
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        DungeonCatalogPayload payload = dungeonCatalogPayload(server);
        server.getPlayerList().getPlayers().forEach(player -> send(player, payload));
    }

    private static DungeonCatalogPayload dungeonCatalogPayload(net.minecraft.server.MinecraftServer server) {
        var registry = DungeonDefinitionRegistry.INSTANCE;
        var dungeons = registry.ids().stream()
            .map(id -> dungeonEntry(server, registry, id))
            .toList();
        var pools = registry.structurePools().entrySet().stream()
            .map(entry -> new DungeonCatalogPayload.StructurePoolEntry(entry.getKey(), entry.getValue()))
            .toList();
        return new DungeonCatalogPayload(
            ClientCacheIdentitySavedData.get(server), dungeons, pools);
    }

    private static DungeonCatalogPayload.DungeonEntry dungeonEntry(
        net.minecraft.server.MinecraftServer server,
        DungeonDefinitionRegistry registry,
        ResourceLocation dungeonId
    ) {
        var definition = registry.get(dungeonId).orElseThrow();
        java.util.UUID previewId = java.util.UUID.nameUUIDFromBytes(
            dungeonId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long previewSeed = SeedDerivation.derive(
            server.getWorldData().worldGenOptions().seed(), previewId, "catalog_portal/" + dungeonId);
        ResolvedPortalColors colors;
        try {
            colors = PortalAppearanceResolver.preview(server.registryAccess(), definition, previewSeed);
        } catch (com.cappleapple.instancednotinfinite.definition.ResolutionException exception) {
            InstancedNotInfinite.LOGGER.warn(
                "Could not select a preview biome for dungeon {}; using configured portal fallbacks", dungeonId, exception);
            colors = PortalAppearanceResolver.configured(definition, java.util.OptionalInt.empty());
        }
        return new DungeonCatalogPayload.DungeonEntry(
            dungeonId, colors.innerColor(), colors.outerColor(), !registry.poolOnlySuppresses(dungeonId));
    }

    public static void broadcastBatch(DungeonManifestation value, List<VisualBlock> blocks) {
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION, value.originDimension()));
        if (level == null) return;
        for (int offset = 0; offset < blocks.size(); offset += BLOCKS_PER_PACKET) {
            List<VisualBlock> batch = blocks.subList(offset, Math.min(blocks.size(), offset + BLOCKS_PER_PACKET));
            sendNear(
                level, null, value.origin().getX(), value.origin().getY(), value.origin().getZ(),
                ServerConfig.INSTANCE.manifestationRenderDistance.get(),
                new ManifestationBlocksPayload(value.id(), batch, true));
        }
    }

    public static void broadcastProgress(DungeonManifestation value) {
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION, value.originDimension()));
        if (level == null) return;
        DungeonInstanceManager.PortalCountdown countdown = portalCountdown(value, level);
        sendNear(
            level, null, value.origin().getX(), value.origin().getY(), value.origin().getZ(),
            ServerConfig.INSTANCE.manifestationRenderDistance.get(),
            new ManifestationProgressPayload(value.id(), value.state(), (float)value.generationProgress(),
                (float)value.animationProgress(), value.stateChangedAtGameTime(),
                value.portalColors().innerColor(), value.portalColors().outerColor(),
                countdown.totalTicks(), countdown.remainingTicks(), countdown.countingDown()));
    }

    public static void removeForNearby(DungeonManifestation value) {
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION, value.originDimension()));
        if (level != null) sendNear(
            level, null, value.origin().getX(), value.origin().getY(), value.origin().getZ(),
            ServerConfig.INSTANCE.manifestationRenderDistance.get(), new ManifestationRemovePayload(value.id()));
        KNOWN.values().forEach(ids -> ids.remove(value.id()));
    }

    private static void sendFull(DungeonManifestationManager manager, ServerPlayer player, DungeonManifestation value) {
        DungeonVisualSnapshot snapshot = manager.snapshot(value.id()).orElse(null);
        BoundingBox bounds = snapshot != null ? snapshot.bounds() : DungeonInstanceManager.get(player.getServer())
            .get(value.instanceId()).flatMap(instance -> instance.plan()).map(plan -> {
                BoundingBox box = plan.envelopeBounds();
                return new BoundingBox(0, 0, 0, box.getXSpan() - 1, box.getYSpan() - 1, box.getZSpan() - 1);
            }).orElse(new BoundingBox(0, 0, 0, 0, 0, 0));
        BoundingBox visualBounds = manager.visualBounds(value.id()).orElse(bounds);
        send(player, startPayload(value, bounds, visualBounds));
        if (snapshot != null) {
            List<VisualBlock> blocks = snapshot.blocks();
            for (int offset = 0; offset < blocks.size(); offset += BLOCKS_PER_PACKET) {
                send(player, new ManifestationBlocksPayload(
                    value.id(), blocks.subList(offset, Math.min(blocks.size(), offset + BLOCKS_PER_PACKET)), true));
            }
        }
        ServerLevel level = player.serverLevel();
        DungeonInstanceManager.PortalCountdown countdown = portalCountdown(value, level);
        send(player, new ManifestationProgressPayload(
            value.id(), value.state(), (float)value.generationProgress(), (float)value.animationProgress(), value.stateChangedAtGameTime(),
            value.portalColors().innerColor(), value.portalColors().outerColor(),
            countdown.totalTicks(), countdown.remainingTicks(), countdown.countingDown()));
    }

    private static ManifestationStartPayload startPayload(
        DungeonManifestation value,
        BoundingBox bounds,
        BoundingBox visualBounds
    ) {
        return new ManifestationStartPayload(
            value.id(), value.originDimension(), value.origin(), value.rotationDegrees(), value.instanceId().value(), value.dungeonId(),
            value.animationSeed(), value.animationMode(), value.state(), (float)value.generationProgress(),
            (float)value.animationProgress(), value.stateChangedAtGameTime(), bounds.getXSpan(), bounds.getYSpan(), bounds.getZSpan(),
            visualBounds.minX(), visualBounds.minY(), visualBounds.minZ(),
            visualBounds.maxX(), visualBounds.maxY(), visualBounds.maxZ(),
            ServerConfig.INSTANCE.hologramMaxWidth.get().floatValue(), ServerConfig.INSTANCE.hologramMaxHeight.get().floatValue(),
            ServerConfig.INSTANCE.hologramMaxDepth.get().floatValue(), ServerConfig.INSTANCE.terrainAlpha.get().floatValue(),
            ServerConfig.INSTANCE.structureAlpha.get().floatValue(), ServerConfig.INSTANCE.collapseDurationTicks.get(),
            ServerConfig.INSTANCE.portalGrowthDurationTicks.get(), ServerConfig.INSTANCE.portalCloseDurationTicks.get(),
            ServerConfig.INSTANCE.portalWidth.get().floatValue(), ServerConfig.INSTANCE.portalHeight.get().floatValue(),
            ServerConfig.INSTANCE.portalDepth.get().floatValue(),
            ServerConfig.INSTANCE.portalMinimumWidth.get().floatValue(), ServerConfig.INSTANCE.portalMinimumHeight.get().floatValue(),
            ServerConfig.INSTANCE.portalMinimumDepth.get().floatValue(),
            value.portalColors().innerColor(), value.portalColors().outerColor(),
            ServerConfig.INSTANCE.preparationParticleStyle.get(),
            ParticleColor.parseRgb(ServerConfig.INSTANCE.preparationParticleColor.get()),
            ServerConfig.INSTANCE.preparationParticleRate.get(),
            ServerConfig.INSTANCE.preparationParticleScale.get().floatValue(),
            ServerConfig.INSTANCE.preparationParticleRadius.get().floatValue());
    }

    private static DungeonInstanceManager.PortalCountdown portalCountdown(
        DungeonManifestation value,
        ServerLevel originLevel
    ) {
        DungeonInstanceManager.PortalCountdown instanceCountdown =
            DungeonInstanceManager.get(originLevel.getServer()).portalCountdown(value.instanceId());
        int portalLifetimeMinutes = ServerConfig.INSTANCE.portalLifetimeMinutes.get();
        if (portalLifetimeMinutes <= 0 || value.state() != com.cappleapple.instancednotinfinite.manifestation.ManifestationState.PORTAL_OPEN) {
            return instanceCountdown;
        }
        int totalTicks = portalLifetimeMinutes * 1_200;
        int remainingTicks = (int)Math.max(
            0L, totalTicks - (originLevel.getGameTime() - value.stateChangedAtGameTime()));
        if (!instanceCountdown.countingDown() || remainingTicks < instanceCountdown.remainingTicks()) {
            return new DungeonInstanceManager.PortalCountdown(totalTicks, remainingTicks, true);
        }
        return instanceCountdown;
    }

    public static void clear() { KNOWN.clear(); }

    private static boolean canSend(ServerPlayer player, ResourceLocation payloadId) {
        return NetworkRegistry.hasChannel(player.connection, payloadId);
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (canSend(player, payload.type().id())) {
            player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(payload));
        }
    }

    private static void sendNear(
        ServerLevel level,
        ServerPlayer excluded,
        double x,
        double y,
        double z,
        double radius,
        CustomPacketPayload payload
    ) {
        double radiusSquared = radius * radius;
        level.players().stream()
            .filter(player -> player != excluded)
            .filter(player -> player.distanceToSqr(x, y, z) <= radiusSquared)
            .forEach(player -> send(player, payload));
    }
}
