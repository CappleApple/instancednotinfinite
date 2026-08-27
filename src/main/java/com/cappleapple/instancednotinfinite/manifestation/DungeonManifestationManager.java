package com.cappleapple.instancednotinfinite.manifestation;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.api.event.DungeonManifestationReadyEvent;
import com.cappleapple.instancednotinfinite.api.event.DungeonManifestationStartingEvent;
import com.cappleapple.instancednotinfinite.api.event.DungeonPortalClosedEvent;
import com.cappleapple.instancednotinfinite.api.event.DungeonPortalOpenedEvent;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.content.ManifestationPortalBlockEntity;
import com.cappleapple.instancednotinfinite.content.ManifestationTargetComponent;
import com.cappleapple.instancednotinfinite.content.ModContent;
import com.cappleapple.instancednotinfinite.instance.DungeonGenerationJob;
import com.cappleapple.instancednotinfinite.instance.DungeonInstanceManager;
import com.cappleapple.instancednotinfinite.instance.InstanceOperationException;
import com.cappleapple.instancednotinfinite.instance.SeedDerivation;
import com.cappleapple.instancednotinfinite.snapshot.DungeonVisualSnapshot;
import com.cappleapple.instancednotinfinite.snapshot.VisualBlock;
import com.cappleapple.instancednotinfinite.network.ManifestationNetwork;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;

/** Owns persistent overworld presentation state separately from temporary instance state. */
public final class DungeonManifestationManager implements AutoCloseable {
    private static DungeonManifestationManager active;

    private final MinecraftServer server;
    private final DungeonManifestationSavedData data;
    private final Map<UUID, DungeonGenerationJob> jobs = new HashMap<>();
    private final Map<UUID, DungeonVisualSnapshot> snapshots = new HashMap<>();
    private BiConsumer<DungeonManifestation, List<VisualBlock>> snapshotBatchListener = (manifestation, blocks) -> {};
    private int saveTicker;

    private DungeonManifestationManager(MinecraftServer server) {
        this.server = server;
        this.data = DungeonManifestationSavedData.get(server);
    }

    public static DungeonManifestationManager start(MinecraftServer server) {
        if (active != null) active.close();
        active = new DungeonManifestationManager(server);
        active.recover();
        return active;
    }

    public static DungeonManifestationManager get(MinecraftServer server) {
        if (active == null || active.server != server) {
            throw new IllegalStateException("Dungeon manifestation manager is not running for this server");
        }
        return active;
    }

    public static Optional<DungeonManifestationManager> current() {
        return Optional.ofNullable(active);
    }

    public DungeonManifestation spawn(
        ServerLevel level,
        BlockPos origin,
        DungeonTarget target,
        ManifestationOptions options,
        ServerPlayer initiator
    ) throws InstanceOperationException {
        requireServerThread();
        if (!ServerConfig.INSTANCE.manifestationEnabled.get()) {
            throw new InstanceOperationException("Dungeon manifestations are disabled by server configuration");
        }
        validateOrigin(level, origin);
        UUID id = UUID.randomUUID();
        DungeonGenerationJob job = switch (target.kind()) {
            case DUNGEON -> DungeonInstanceManager.get(server).beginGeneration(
                target.id().orElseThrow(), options.lifecycleOverrides(), blocks -> onSnapshotBatch(id, blocks));
            case CONFIGURED_POOL -> DungeonInstanceManager.get(server).beginRandomGeneration(
                options.lifecycleOverrides(), blocks -> onSnapshotBatch(id, blocks));
            case STRUCTURE_POOL -> DungeonInstanceManager.get(server).beginStructurePoolGeneration(
                target.id().orElseThrow(), options.lifecycleOverrides(), blocks -> onSnapshotBatch(id, blocks));
        };
        ResourceLocation dungeonId = ResourceLocation.parse(job.instance().definition().id());
        long animationSeed = SeedDerivation.derive(job.instance().seed(), id, "manifestation_animation");
        AnimationMode requested = options.animationMode() == AnimationMode.RANDOM_MODE
            ? ServerConfig.INSTANCE.defaultAnimationMode.get()
            : options.animationMode();
        AnimationMode mode = ManifestationScorer.resolveMode(requested, animationSeed, allowedModes());
        int minimum = ServerConfig.INSTANCE.animationDurationMinimumTicks.get();
        int maximum = Math.max(minimum, ServerConfig.INSTANCE.animationDurationMaximumTicks.get());
        int duration = minimum + Math.floorMod((int)(animationSeed ^ (animationSeed >>> 32)), maximum - minimum + 1);
        DungeonManifestation manifestation = new DungeonManifestation(
            id, level.dimension().location(), origin, options.rotationDegrees(), job.instance().id(), dungeonId, target,
            options.lifecycleOverrides(),
            animationSeed, mode, initiator == null ? null : initiator.getUUID(),
            System.currentTimeMillis(), level.getGameTime(), duration,
            PortalAppearanceResolver.configured(
                job.instance().definition(), java.util.OptionalInt.of(job.biomeFogColor())));
        manifestation.transition(ManifestationState.GENERATING, level.getGameTime());
        this.jobs.put(id, job);
        this.data.put(manifestation);
        NeoForge.EVENT_BUS.post(new DungeonManifestationStartingEvent(manifestation));
        InstancedNotInfinite.LOGGER.info(
            "[Manifestation {}] Starting dungeon {} as instance {}; mode={}", shortId(id), dungeonId,
            job.instance().id().shortId(), mode);
        return manifestation;
    }

    public Optional<DungeonManifestation> get(UUID id) { return data.get(id); }

    public Optional<DungeonManifestation> getAt(ServerLevel level, BlockPos origin) {
        return data.values().stream()
            .filter(value -> value.originDimension().equals(level.dimension().location()))
            .filter(value -> value.origin().equals(origin))
            .filter(value -> !value.state().terminal())
            .findFirst();
    }

    public Collection<DungeonManifestation> values() {
        return data.values().stream().sorted(Comparator.comparing(DungeonManifestation::createdAtMillis)).toList();
    }

    public void tryActivatePortal(ServerPlayer player) {
        if (player.isOnPortalCooldown()) return;
        for (DungeonManifestation value : data.values()) {
            if (value.state() != ManifestationState.PORTAL_OPEN
                || !value.originDimension().equals(player.level().dimension().location())) continue;
            ServerLevel level = player.serverLevel();
            if (level.getBlockEntity(value.origin()) instanceof ManifestationPortalBlockEntity portal
                && portal.manifestationId().filter(value.id()::equals).isPresent()
                && portal.intersects(player.getBoundingBox())) {
                com.cappleapple.instancednotinfinite.content.ManifestationPortalBlock.tryActivate(
                    level, value.origin(), player);
                return;
            }
        }
    }

    public Optional<DungeonVisualSnapshot> snapshot(UUID id) {
        DungeonVisualSnapshot complete = snapshots.get(id);
        if (complete != null) return Optional.of(complete);
        DungeonGenerationJob job = jobs.get(id);
        return job == null ? Optional.empty() : Optional.of(job.currentSnapshot());
    }

    public Optional<net.minecraft.world.level.levelgen.structure.BoundingBox> visualBounds(UUID id) {
        DungeonGenerationJob job = jobs.get(id);
        return job == null ? Optional.empty() : Optional.of(job.visualBounds());
    }

    public void markItemConsumed(UUID id, CatalystConsumptionPolicy policy) throws InstanceOperationException {
        DungeonManifestation value = data.get(id)
            .orElseThrow(() -> new InstanceOperationException("Unknown dungeon manifestation " + id));
        value.markItemConsumed(policy);
        data.changed();
    }

    /** Delivers a persisted failure refund when its owner next joins the server. */
    public void recoverPlayer(ServerPlayer player) {
        requireServerThread();
        data.values().stream()
            .filter(value -> value.initiator().filter(player.getUUID()::equals).isPresent())
            .filter(value -> value.state() == ManifestationState.FAILED || value.state() == ManifestationState.CANCELLED)
            .forEach(this::refundIfRequired);
    }

    public void cancel(UUID id, String reason) throws InstanceOperationException {
        requireServerThread();
        DungeonManifestation value = data.get(id)
            .orElseThrow(() -> new InstanceOperationException("Unknown dungeon manifestation " + id));
        if (value.state().terminal()) return;
        long gameTime = originLevel(value).map(ServerLevel::getGameTime).orElse(server.overworld().getGameTime());
        if (value.state() == ManifestationState.PORTAL_OPENING
            || value.state() == ManifestationState.PORTAL_OPEN
            || value.state() == ManifestationState.CLOSING) {
            if (value.state() == ManifestationState.CLOSING) value.cancelAfterClosing();
            else value.beginClosing(gameTime, ManifestationState.CANCELLED);
            DungeonGenerationJob cancelledJob = jobs.remove(id);
            if (cancelledJob != null) cancelledJob.releaseTickets();
            data.changed();
            DungeonInstanceManager.get(server).cancelCreation(value.instanceId(), "Manifestation cancelled: " + reason);
            InstancedNotInfinite.LOGGER.info("[Manifestation {}] Closing after cancellation: {}", shortId(id), reason);
            return;
        }
        value.transition(ManifestationState.CANCELLED, gameTime);
        DungeonGenerationJob cancelledJob = jobs.remove(id);
        if (cancelledJob != null) cancelledJob.releaseTickets();
        snapshots.remove(id);
        data.changed();
        DungeonInstanceManager.get(server).cancelCreation(value.instanceId(), "Manifestation cancelled: " + reason);
        refundIfRequired(value);
        ManifestationNetwork.removeForNearby(value);
        InstancedNotInfinite.LOGGER.info("[Manifestation {}] Cancelled: {}", shortId(id), reason);
    }

    public void finishAnimation(UUID id) throws InstanceOperationException {
        DungeonManifestation value = data.get(id)
            .orElseThrow(() -> new InstanceOperationException("Unknown dungeon manifestation " + id));
        value.forceAnimationComplete();
        data.changed();
    }

    public void tick() {
        requireServerThread();
        for (DungeonManifestation value : List.copyOf(data.values())) {
            try {
                tickOne(value);
            } catch (Exception exception) {
                fail(value, exception);
            }
        }
        if (saveTicker % 5 == 0) data.values().forEach(ManifestationNetwork::broadcastProgress);
        if (++saveTicker % 20 == 0) {
            pruneOldTerminalRecords();
            data.changed();
            ManifestationNetwork.syncNearby(this);
        }
    }

    private void tickOne(DungeonManifestation value) throws InstanceOperationException {
        ServerLevel level = originLevel(value).orElse(null);
        if (level == null || value.state().terminal()) return;
        long now = level.getGameTime();
        DungeonGenerationJob job = jobs.get(value.id());
        if (job != null && !job.complete()) DungeonInstanceManager.get(server).advanceGeneration(job);
        if (job != null) {
            // Terrain generation and heightmap priming can take many seconds for a large
            // structure, but there is nothing visual to reveal during that work. The first
            // captured structure batch transitions to MANIFESTING and establishes this
            // state's timestamp as the beginning of the configured animation duration.
            if (job.complete() && value.state() == ManifestationState.GENERATING) {
                value.transition(ManifestationState.MANIFESTING, now);
            }
            double timed = value.animationForced()
                ? 1.0
                : value.state() == ManifestationState.GENERATING
                    ? 0.0
                    : Math.min(1.0, Math.max(0.0,
                        (now - value.stateChangedAtGameTime()) / (double)value.animationDurationTicks()));
            value.updateProgress(job.progress(), Math.min(timed, job.progress()));
            if (job.complete()) {
                if (!job.portalColors().equals(value.portalColors())) {
                    value.setPortalColors(job.portalColors());
                    data.changed();
                }
                job.snapshot().ifPresent(snapshot -> snapshots.put(value.id(), snapshot));
                if (value.animationProgress() >= 1.0) {
                    value.transition(ManifestationState.FINALIZING, now);
                    NeoForge.EVENT_BUS.post(new DungeonManifestationReadyEvent(value));
                    value.transition(ManifestationState.COLLAPSING, now);
                    jobs.remove(value.id());
                    InstancedNotInfinite.LOGGER.info("[Manifestation {}] Instance ready; collapsing", shortId(value.id()));
                }
            }
        }
        if (value.state() == ManifestationState.COLLAPSING
            && now - value.stateChangedAtGameTime() >= ServerConfig.INSTANCE.collapseDurationTicks.get()) {
            openPortal(level, value);
            value.transition(ManifestationState.PORTAL_OPENING, now);
            InstancedNotInfinite.LOGGER.info("[Manifestation {}] Portal opening", shortId(value.id()));
        }
        if (value.state() == ManifestationState.PORTAL_OPENING
            && now - value.stateChangedAtGameTime() >= ServerConfig.INSTANCE.portalGrowthDurationTicks.get()) {
            value.transition(ManifestationState.PORTAL_OPEN, now);
            NeoForge.EVENT_BUS.post(new DungeonPortalOpenedEvent(value));
            InstancedNotInfinite.LOGGER.info("[Manifestation {}] Portal open", shortId(value.id()));
        }
        if (value.state() == ManifestationState.PORTAL_OPEN && shouldClosePortal(value, now)) {
            value.beginClosing(now, ManifestationState.COMPLETE);
            data.changed();
            InstancedNotInfinite.LOGGER.info("[Manifestation {}] Portal closing", shortId(value.id()));
        }
        if (value.state() == ManifestationState.CLOSING
            && now - value.stateChangedAtGameTime() >= ServerConfig.INSTANCE.portalCloseDurationTicks.get()) {
            removePortal(level, value);
            NeoForge.EVENT_BUS.post(new DungeonPortalClosedEvent(value));
            value.transition(value.closingOutcome(), now);
            snapshots.remove(value.id());
            if (value.state() == ManifestationState.CANCELLED) refundIfRequired(value);
            data.changed();
            ManifestationNetwork.removeForNearby(value);
            InstancedNotInfinite.LOGGER.info("[Manifestation {}] Portal closed", shortId(value.id()));
        }
    }

    private boolean shouldClosePortal(DungeonManifestation value, long now) {
        boolean missingInstance = DungeonInstanceManager.get(server).get(value.instanceId())
            .map(instance -> instance.state() == com.cappleapple.instancednotinfinite.instance.InstanceState.UNLOADING
                || instance.state() == com.cappleapple.instancednotinfinite.instance.InstanceState.DELETE_PENDING
                || instance.state() == com.cappleapple.instancednotinfinite.instance.InstanceState.DELETED
                || instance.state() == com.cappleapple.instancednotinfinite.instance.InstanceState.FAILED)
            .orElse(true);
        int lifetime = ServerConfig.INSTANCE.portalLifetimeMinutes.get();
        boolean timedOut = lifetime > 0 && now - value.stateChangedAtGameTime() >= lifetime * 1200L;
        return missingInstance || timedOut;
    }

    private void fail(DungeonManifestation value, Exception exception) {
        long gameTime = originLevel(value).map(ServerLevel::getGameTime).orElse(server.overworld().getGameTime());
        value.fail(exception.getClass().getSimpleName() + ": " + exception.getMessage(), gameTime);
        DungeonGenerationJob failedJob = jobs.remove(value.id());
        if (failedJob != null) failedJob.releaseTickets();
        snapshots.remove(value.id());
        data.changed();
        try {
            DungeonInstanceManager.get(server).cancelCreation(value.instanceId(), "Manifestation failed");
        } catch (Exception cleanupFailure) {
            exception.addSuppressed(cleanupFailure);
        }
        refundIfRequired(value);
        value.initiator().map(server.getPlayerList()::getPlayer).ifPresent(player ->
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Dungeon manifestation failed: " + exception.getMessage())));
        InstancedNotInfinite.LOGGER.error("[Manifestation {}] Failed; portal will not open", shortId(value.id()), exception);
    }

    private void recover() {
        long now = server.overworld().getGameTime();
        for (DungeonManifestation value : new ArrayList<>(data.values())) {
            DungeonInstanceManager.get(server).get(value.instanceId()).ifPresent(instance ->
                value.setPortalColors(instance.portalColors().orElseGet(() -> {
                    net.minecraft.core.Registry<net.minecraft.world.level.biome.Biome> biomes =
                        server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
                    int fogColor = biomes.getHolder(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.BIOME, instance.biomeId()))
                        .map(holder -> holder.value().getFogColor())
                        .orElse(0);
                    return PortalAppearanceResolver.configured(
                        instance.definition(), fogColor == 0
                            ? java.util.OptionalInt.empty()
                            : java.util.OptionalInt.of(fogColor));
                })));
            if (value.state() == ManifestationState.PORTAL_OPEN || value.state() == ManifestationState.CLOSING) {
                originLevel(value).ifPresent(level -> openPortal(level, value));
                continue;
            }
            if (!value.state().terminal()) {
                value.fail("Server restarted during manifestation; conservative cancellation and instance cleanup", now);
                try {
                    DungeonInstanceManager.get(server).get(value.instanceId()).ifPresent(instance -> {
                        try {
                            DungeonInstanceManager.get(server).cancelCreation(value.instanceId(), "Interrupted manifestation recovery");
                        } catch (InstanceOperationException exception) {
                            InstancedNotInfinite.LOGGER.error("Could not clean interrupted manifestation instance {}", value.instanceId(), exception);
                        }
                    });
                } catch (RuntimeException exception) {
                    InstancedNotInfinite.LOGGER.error("Could not inspect interrupted manifestation instance {}", value.instanceId(), exception);
                }
                refundIfRequired(value);
            }
        }
        data.changed();
    }

    public void setSnapshotBatchListener(BiConsumer<DungeonManifestation, List<VisualBlock>> listener) {
        this.snapshotBatchListener = listener;
    }

    private void onSnapshotBatch(UUID id, List<VisualBlock> blocks) {
        if (blocks.isEmpty()) return;
        data.get(id).ifPresent(value -> {
            if (value.state() == ManifestationState.GENERATING) {
                long now = originLevel(value).map(ServerLevel::getGameTime).orElse(server.overworld().getGameTime());
                value.transition(ManifestationState.MANIFESTING, now);
                data.changed();
            }
            snapshotBatchListener.accept(value, blocks);
        });
    }

    private void validateOrigin(ServerLevel level, BlockPos origin) throws InstanceOperationException {
        if (!level.getWorldBorder().isWithinBounds(origin)) {
            throw new InstanceOperationException("Manifestation origin is outside the world border");
        }
        if (!level.getBlockState(origin).canBeReplaced() || !level.getBlockState(origin.above()).canBeReplaced()) {
            throw new InstanceOperationException("Manifestation portal requires two replaceable blocks at " + origin.toShortString());
        }
        int separation = Math.max(2, (int)Math.ceil(ServerConfig.INSTANCE.portalWidth.get()));
        boolean occupied = data.values().stream()
            .filter(value -> value.originDimension().equals(level.dimension().location()))
            .filter(value -> !value.state().terminal())
            .anyMatch(value -> value.origin().distManhattan(origin) <= separation);
        if (occupied) throw new InstanceOperationException("Another manifestation already occupies this portal area");
    }

    private void openPortal(ServerLevel level, DungeonManifestation value) {
        level.setBlock(value.origin(), ModContent.MANIFESTATION_PORTAL.get().defaultBlockState(), 3);
        if (level.getBlockEntity(value.origin()) instanceof ManifestationPortalBlockEntity portal) {
            portal.bind(
                value.id(), value.rotationDegrees(),
                ServerConfig.INSTANCE.portalWidth.get().floatValue(), ServerConfig.INSTANCE.portalHeight.get().floatValue(),
                ServerConfig.INSTANCE.portalDepth.get().floatValue(),
                ServerConfig.INSTANCE.portalMinimumWidth.get().floatValue(), ServerConfig.INSTANCE.portalMinimumHeight.get().floatValue(),
                ServerConfig.INSTANCE.portalMinimumDepth.get().floatValue(),
                value.portalColors().innerColor(), value.portalColors().outerColor());
        } else {
            throw new IllegalStateException("Manifestation portal block entity was not created at " + value.origin());
        }
    }

    private void removePortal(ServerLevel level, DungeonManifestation value) {
        if (level.getBlockEntity(value.origin()) instanceof ManifestationPortalBlockEntity portal
            && portal.manifestationId().filter(value.id()::equals).isPresent()) {
            level.removeBlock(value.origin(), false);
        }
    }

    private void refundIfRequired(DungeonManifestation value) {
        boolean mustRefund = value.catalystConsumptionPolicy() == CatalystConsumptionPolicy.ON_SUCCESS
            || ServerConfig.INSTANCE.refundOnFailure.get();
        if (!value.itemConsumed() || value.itemRefunded() || !mustRefund) return;
        value.initiator().map(server.getPlayerList()::getPlayer).ifPresent(player -> {
            net.minecraft.world.item.ItemStack refund = new net.minecraft.world.item.ItemStack(ModContent.MANIFESTATION_CATALYST.get());
            refund.set(ModContent.MANIFESTATION_TARGET.get(), ManifestationTargetComponent.fromTarget(value.target()));
            if (!value.lifecycleOverrides().isEmpty()) {
                refund.set(ModContent.INSTANCE_LIFECYCLE.get(), value.lifecycleOverrides());
            }
            if (!player.getInventory().add(refund)) player.drop(refund, false);
            value.markItemRefunded();
            data.changed();
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("The failed manifestation catalyst was refunded"));
        });
    }

    private void pruneOldTerminalRecords() {
        long now = server.overworld().getGameTime();
        data.values().stream()
            .filter(value -> value.state().terminal())
            .filter(value -> value.state() == ManifestationState.COMPLETE
                || !value.itemConsumed() || value.itemRefunded()
                || (value.catalystConsumptionPolicy() == CatalystConsumptionPolicy.ON_ACTIVATION
                    && !ServerConfig.INSTANCE.refundOnFailure.get()))
            .filter(value -> now - value.stateChangedAtGameTime() >= 1200L)
            .map(DungeonManifestation::id)
            .toList()
            .forEach(data::remove);
    }

    private Optional<ServerLevel> originLevel(DungeonManifestation value) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, value.originDimension());
        return Optional.ofNullable(server.getLevel(key));
    }

    private static List<AnimationMode> allowedModes() {
        List<AnimationMode> modes = ServerConfig.INSTANCE.allowedRandomModes.get().stream().map(value -> {
            try { return AnimationMode.parse(value); }
            catch (IllegalArgumentException exception) { return null; }
        }).filter(java.util.Objects::nonNull).filter(mode -> mode != AnimationMode.RANDOM_MODE).toList();
        return modes.isEmpty() ? List.of(AnimationMode.GROUND_UP) : modes;
    }

    private void requireServerThread() {
        if (!server.isSameThread()) throw new IllegalStateException("Manifestation operations must run on the server thread");
    }

    private static String shortId(UUID id) { return id.toString().substring(0, 8); }

    @Override
    public void close() {
        data.changed();
        server.overworld().getDataStorage().save();
        jobs.values().forEach(DungeonGenerationJob::releaseTickets);
        jobs.clear();
        snapshots.clear();
        if (active == this) active = null;
    }
}
