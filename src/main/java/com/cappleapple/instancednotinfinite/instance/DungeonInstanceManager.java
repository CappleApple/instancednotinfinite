package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.api.event.DungeonCompletedEvent;
import com.cappleapple.instancednotinfinite.api.event.DungeonInstanceCreatedEvent;
import com.cappleapple.instancednotinfinite.api.event.DungeonInstanceDeletingEvent;
import com.cappleapple.instancednotinfinite.api.event.DungeonPlayerEnteredEvent;
import com.cappleapple.instancednotinfinite.backend.DynamicLevelBackend;
import com.cappleapple.instancednotinfinite.backend.VanillaDynamicLevelBackend;
import com.cappleapple.instancednotinfinite.cleanup.InstanceCleanupManager;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.content.ManifestationPortalBlockEntity;
import com.cappleapple.instancednotinfinite.content.ModContent;
import com.cappleapple.instancednotinfinite.definition.DefinitionResolver;
import com.cappleapple.instancednotinfinite.definition.DungeonDefinition;
import com.cappleapple.instancednotinfinite.definition.DungeonDefinitionRegistry;
import com.cappleapple.instancednotinfinite.definition.ReentryPolicy;
import com.cappleapple.instancednotinfinite.definition.ResolutionException;
import com.cappleapple.instancednotinfinite.definition.ResolvedDungeonDefinition;
import com.cappleapple.instancednotinfinite.definition.TerrainSettings;
import com.cappleapple.instancednotinfinite.player.PlayerReturnManager;
import com.cappleapple.instancednotinfinite.manifestation.PortalAppearanceResolver;
import com.cappleapple.instancednotinfinite.manifestation.ResolvedPortalColors;
import com.cappleapple.instancednotinfinite.structure.DungeonStructurePlacer;
import com.cappleapple.instancednotinfinite.structure.DungeonStructurePlacer.PreparedStructure;
import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import com.cappleapple.instancednotinfinite.snapshot.VisualBlock;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;

public final class DungeonInstanceManager implements AutoCloseable {
    private static DungeonInstanceManager active;

    private final MinecraftServer server;
    private final DungeonInstanceSavedData data;
    private final DynamicLevelBackend backend;
    private final DungeonStructurePlacer structurePlacer;
    private final PlayerReturnManager returns;
    private final InstanceCleanupManager cleanup;
    private final Map<InstanceId, Integer> unloadRequestedAtTick = new HashMap<>();
    private int tickCounter;

    private DungeonInstanceManager(MinecraftServer server) {
        this.server = server;
        this.data = DungeonInstanceSavedData.get(server);
        this.backend = new VanillaDynamicLevelBackend();
        this.structurePlacer = new DungeonStructurePlacer();
        this.returns = new PlayerReturnManager();
        this.cleanup = new InstanceCleanupManager();
    }

    public static DungeonInstanceManager start(MinecraftServer server) {
        if (active != null) {
            active.close();
        }
        active = new DungeonInstanceManager(server);
        active.rebuildCatalogue();
        active.recover();
        return active;
    }

    public static DungeonInstanceManager get(MinecraftServer server) {
        if (active == null || active.server != server) {
            throw new IllegalStateException("Dungeon instance manager is not running for this server");
        }
        return active;
    }

    public static Optional<DungeonInstanceManager> current() {
        return Optional.ofNullable(active);
    }

    public DungeonInstance create(ResourceLocation dungeonId) throws InstanceOperationException {
        return create(dungeonId, InstanceLifecycleOverrides.empty());
    }

    public DungeonInstance create(ResourceLocation dungeonId, InstanceLifecycleOverrides lifecycleOverrides)
        throws InstanceOperationException {
        DungeonGenerationJob job = new DungeonGenerationJob(
            this, prepareCreation(dungeonId, InstanceId.random(), lifecycleOverrides), 1, false, ignored -> {});
        while (!job.complete()) {
            job.advance(Double.MAX_VALUE, Integer.MAX_VALUE);
        }
        return job.instance();
    }

    public DungeonInstance createRandom() throws InstanceOperationException {
        return createRandom(InstanceLifecycleOverrides.empty());
    }

    public DungeonInstance createRandom(InstanceLifecycleOverrides lifecycleOverrides) throws InstanceOperationException {
        requireServerThread();
        InstanceId id = InstanceId.random();
        long selectionSeed = SeedDerivation.derive(
            this.server.getWorldData().worldGenOptions().seed(), id.value(), "definition_pool");
        ResourceLocation selected = DungeonDefinitionRegistry.INSTANCE.select(selectionSeed)
            .orElseThrow(() -> new InstanceOperationException("No valid dungeon definitions are loaded"));
        DungeonGenerationJob job = new DungeonGenerationJob(
            this, prepareCreation(selected, id, lifecycleOverrides), 1, false, ignored -> {});
        while (!job.complete()) {
            job.advance(Double.MAX_VALUE, Integer.MAX_VALUE);
        }
        return job.instance();
    }

    public DungeonGenerationJob beginGeneration(
        ResourceLocation dungeonId,
        Consumer<List<VisualBlock>> snapshotBatchConsumer
    ) throws InstanceOperationException {
        return beginGeneration(dungeonId, InstanceLifecycleOverrides.empty(), snapshotBatchConsumer);
    }

    public DungeonGenerationJob beginGeneration(
        ResourceLocation dungeonId,
        InstanceLifecycleOverrides lifecycleOverrides,
        Consumer<List<VisualBlock>> snapshotBatchConsumer
    ) throws InstanceOperationException {
        return new DungeonGenerationJob(
            this, prepareCreation(dungeonId, InstanceId.random(), lifecycleOverrides),
            ServerConfig.INSTANCE.maximumSnapshotBlocks.get(), true, snapshotBatchConsumer);
    }

    public DungeonGenerationJob beginRandomGeneration(
        Consumer<List<VisualBlock>> snapshotBatchConsumer
    ) throws InstanceOperationException {
        return beginRandomGeneration(InstanceLifecycleOverrides.empty(), snapshotBatchConsumer);
    }

    public DungeonGenerationJob beginRandomGeneration(
        InstanceLifecycleOverrides lifecycleOverrides,
        Consumer<List<VisualBlock>> snapshotBatchConsumer
    ) throws InstanceOperationException {
        requireServerThread();
        InstanceId id = InstanceId.random();
        long selectionSeed = SeedDerivation.derive(
            this.server.getWorldData().worldGenOptions().seed(), id.value(), "definition_pool");
        ResourceLocation selected = DungeonDefinitionRegistry.INSTANCE.select(selectionSeed)
            .orElseThrow(() -> new InstanceOperationException("No valid dungeon definitions are loaded"));
        return new DungeonGenerationJob(
            this, prepareCreation(selected, id, lifecycleOverrides),
            ServerConfig.INSTANCE.maximumSnapshotBlocks.get(), true, snapshotBatchConsumer);
    }

    public DungeonGenerationJob beginStructurePoolGeneration(
        ResourceLocation tagId,
        Consumer<List<VisualBlock>> snapshotBatchConsumer
    ) throws InstanceOperationException {
        return beginStructurePoolGeneration(tagId, InstanceLifecycleOverrides.empty(), snapshotBatchConsumer);
    }

    public DungeonGenerationJob beginStructurePoolGeneration(
        ResourceLocation tagId,
        InstanceLifecycleOverrides lifecycleOverrides,
        Consumer<List<VisualBlock>> snapshotBatchConsumer
    ) throws InstanceOperationException {
        requireServerThread();
        InstanceId id = InstanceId.random();
        long selectionSeed = SeedDerivation.derive(
            this.server.getWorldData().worldGenOptions().seed(), id.value(), "structure_pool/" + tagId);
        ResourceLocation selected = DungeonDefinitionRegistry.INSTANCE.selectStructurePool(tagId, selectionSeed)
            .orElseThrow(() -> new InstanceOperationException("No valid dungeon definitions are loaded for structure pool #" + tagId));
        return new DungeonGenerationJob(
            this, prepareCreation(selected, id, lifecycleOverrides),
            ServerConfig.INSTANCE.maximumSnapshotBlocks.get(), true, snapshotBatchConsumer);
    }

    private PreparedDungeonCreation prepareCreation(
        ResourceLocation dungeonId,
        InstanceId id,
        InstanceLifecycleOverrides lifecycleOverrides
    ) throws InstanceOperationException {
        requireServerThread();
        long nonFinal = this.data.values().stream().filter(instance -> instance.state() != InstanceState.DELETED).count();
        if (nonFinal >= ServerConfig.INSTANCE.maximumConcurrentInstances.get()) {
            throw new InstanceOperationException("Maximum concurrent dungeon instances reached (" + nonFinal + ")");
        }
        boolean automaticDefinition = DungeonDefinitionRegistry.INSTANCE.isAutomatic(dungeonId);
        DungeonDefinition sourceDefinition = DungeonDefinitionRegistry.INSTANCE.get(dungeonId)
            .orElseThrow(() -> new InstanceOperationException("Unknown dungeon definition " + dungeonId));
        DungeonDefinition definition = effectiveDefinition(sourceDefinition, automaticDefinition);
        long seed = SeedDerivation.derive(this.server.getWorldData().worldGenOptions().seed(), id.value(), dungeonId.toString());

        ResolvedDungeonDefinition resolved;
        try {
            resolved = DefinitionResolver.resolve(this.server.registryAccess(), this.server.getStructureManager(), definition, seed);
        } catch (ResolutionException exception) {
            throw new InstanceOperationException("Cannot resolve dungeon " + dungeonId + ": " + exception.getMessage(), exception);
        }
        ResourceKey<Level> levelKey = VanillaDynamicLevelBackend.levelKey(id);
        DungeonInstance instance = new DungeonInstance(
            id, definition, levelKey.location(), resolved.structureId(), resolved.structureKind(), resolved.biomeId(), seed,
            System.currentTimeMillis(), lifecycleOverrides.resolve(configuredLifecycle()));
        this.data.put(instance);
        InstancedNotInfinite.LOGGER.info("[Dungeon {}] Creating instance {} using biome {} and seed {}", id.shortId(), dungeonId, resolved.biomeId(), seed);

        try {
            DynamicLevelBackend.CreatedLevel created = this.backend.create(this.server, id, resolved, seed);
            PreparedStructure prepared = this.structurePlacer.prepare(created.level(), resolved, created.generator(), seed);
            GenerationPlan plan = GenerationPlan.fromBounds(
                seed, definition, prepared.bounds(), prepared.origin(), automaticDefinition, prepared.terrainSurfaceY());
            created.generator().updatePlan(plan);
            resizeWorldBorder(created.level(), plan);
            instance.setPlan(plan);
            this.data.changed();
            if (ServerConfig.INSTANCE.debugLogging.get()) {
                InstancedNotInfinite.LOGGER.info(
                    "[Dungeon {}] structureBounds={} envelopeBounds={} origin={} requestedEntry={}",
                    id.shortId(), plan.structureBounds(), plan.envelopeBounds(), plan.structureOrigin(), plan.entryPosition());
            }
            return new PreparedDungeonCreation(
                instance, created, prepared, plan, automaticDefinition, resolved.biome().value().getFogColor());
        } catch (Exception exception) {
            failCreation(instance, exception);
            throw new InstanceOperationException("Dungeon instance " + id.shortId() + " failed: " + exception.getMessage(), exception);
        }
    }

    void finishPreparedCreation(PreparedDungeonCreation creation, ResolvedPortalColors portalColors) throws InstanceOperationException {
        GenerationPlan safePlan = withSafeEntry(
            creation.created().level(), creation.plan(), creation.automaticDefinition(), creation.structure());
        creation.created().generator().updatePlan(safePlan);
        creation.instance().setPlan(safePlan);
        creation.instance().setPortalColors(portalColors);
        placeReturnPortal(creation.created().level(), safePlan, creation.instance().id(), portalColors);
        creation.instance().transition(InstanceState.ACTIVE, System.currentTimeMillis());
        this.data.changed();
        creation.created().level().save(null, true, false);
        NeoForge.EVENT_BUS.post(new DungeonInstanceCreatedEvent(creation.instance()));
        InstancedNotInfinite.LOGGER.info(
            "[Dungeon {}] Incremental generation finished; instance ACTIVE", creation.instance().id().shortId());
    }

    void failPreparedCreation(PreparedDungeonCreation creation, Exception cause) {
        failCreation(creation.instance(), cause);
    }

    DungeonStructurePlacer structurePlacer() {
        return this.structurePlacer;
    }

    public DungeonInstance createAndEnter(ResourceLocation dungeonId, ServerPlayer player) throws InstanceOperationException {
        return createAndEnter(dungeonId, player, InstanceLifecycleOverrides.empty());
    }

    public DungeonInstance createAndEnter(
        ResourceLocation dungeonId,
        ServerPlayer player,
        InstanceLifecycleOverrides lifecycleOverrides
    ) throws InstanceOperationException {
        DungeonInstance instance = create(dungeonId, lifecycleOverrides);
        enter(player, instance.id());
        return instance;
    }

    public DungeonInstance createRandomAndEnter(ServerPlayer player) throws InstanceOperationException {
        return createRandomAndEnter(player, InstanceLifecycleOverrides.empty());
    }

    public DungeonInstance createRandomAndEnter(
        ServerPlayer player,
        InstanceLifecycleOverrides lifecycleOverrides
    ) throws InstanceOperationException {
        DungeonInstance instance = createRandom(lifecycleOverrides);
        enter(player, instance.id());
        return instance;
    }

    public void enter(ServerPlayer player, InstanceId id) throws InstanceOperationException {
        enter(player, id, null, null);
    }

    public void enterFromPortal(
        ServerPlayer player,
        InstanceId id,
        BlockPos sourcePortal,
        int portalRotationDegrees
    ) throws InstanceOperationException {
        enter(player, id, sourcePortal, portalRotationDegrees);
    }

    private void enter(
        ServerPlayer player,
        InstanceId id,
        BlockPos sourcePortal,
        Integer portalRotationDegrees
    ) throws InstanceOperationException {
        requireServerThread();
        DungeonInstance instance = require(id);
        boolean active = instance.state() == InstanceState.ACTIVE || instance.state() == InstanceState.VACANT;
        boolean completedButRetained = instance.state() == InstanceState.COMPLETED
            && instance.definition().reentry() == ReentryPolicy.ALWAYS_UNTIL_DELETED;
        if (!active && !completedButRetained) {
            throw new InstanceOperationException("Instance " + id.shortId() + " is " + instance.state());
        }
        boolean assigned = instance.assignedPlayers().contains(player.getUUID());
        if (assigned && instance.definition().reentry() == ReentryPolicy.NEVER) {
            throw new InstanceOperationException("This dungeon does not allow re-entry");
        }
        ServerLevel level = this.server.getLevel(levelKey(instance));
        if (level == null || instance.plan().isEmpty()) {
            throw new InstanceOperationException("Instance level is not loaded");
        }
        if (sourcePortal == null || portalRotationDegrees == null) {
            this.returns.capture(player, instance);
        } else {
            this.returns.captureFromPortal(
                player, instance, sourcePortal, portalRotationDegrees,
                ServerConfig.INSTANCE.sourcePortalExitOffsetBlocks.get());
        }
        instance.assign(player.getUUID());
        if (instance.state() == InstanceState.VACANT) {
            instance.transition(InstanceState.ACTIVE, System.currentTimeMillis());
        }
        this.data.changed();
        GenerationPlan plan = instance.plan().orElseThrow();
        BlockPos entry = plan.entryPosition();
        player.teleportTo(
            level, entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5,
            plan.entryYaw(), instance.definition().entry().pitch());
        NeoForge.EVENT_BUS.post(new DungeonPlayerEnteredEvent(instance, player));
        InstancedNotInfinite.LOGGER.info("[Dungeon {}] Player {} entered", id.shortId(), player.getGameProfile().getName());
    }

    public boolean leave(ServerPlayer player) {
        requireServerThread();
        Optional<DungeonInstance> instance = getByDimension(player.level().dimension().location());
        if (instance.isEmpty()) {
            return false;
        }
        boolean returned = this.returns.returnPlayer(player);
        return returned;
    }

    public void complete(InstanceId id) throws InstanceOperationException {
        requireServerThread();
        DungeonInstance instance = require(id);
        if (instance.state() != InstanceState.ACTIVE && instance.state() != InstanceState.VACANT) {
            throw new InstanceOperationException("Only ACTIVE or VACANT instances can complete; current state is " + instance.state());
        }
        instance.transition(InstanceState.COMPLETED, System.currentTimeMillis());
        this.data.changed();
        NeoForge.EVENT_BUS.post(new DungeonCompletedEvent(instance));
        InstancedNotInfinite.LOGGER.info("[Dungeon {}] COMPLETED", id.shortId());
    }

    public void delete(InstanceId id) throws InstanceOperationException {
        requireServerThread();
        DungeonInstance instance = require(id);
        returnAll(instance);
        if (onlineInside(instance) > 0) {
            throw new InstanceOperationException("Players remain inside instance " + id.shortId());
        }
        beginUnload(instance);
    }

    /** Cancels an instance that is still being incrementally created and queues normal safe cleanup. */
    public void cancelCreation(InstanceId id, String reason) throws InstanceOperationException {
        requireServerThread();
        DungeonInstance instance = require(id);
        if (instance.state() == InstanceState.CREATING) {
            instance.fail(reason, System.currentTimeMillis());
            this.data.changed();
        }
        delete(id);
    }

    public Optional<DungeonInstance> get(InstanceId id) {
        return this.data.get(id);
    }

    public Optional<DungeonInstance> getPlayerInstance(ServerPlayer player) {
        return getByDimension(player.level().dimension().location())
            .or(() -> this.data.values().stream().filter(instance -> instance.assignedPlayers().contains(player.getUUID())).findFirst());
    }

    public Collection<DungeonInstance> instances() {
        return this.data.values().stream()
            .sorted(Comparator.comparing(instance -> instance.id().toString()))
            .toList();
    }

    public boolean allowsNaturalMobSpawning(ServerLevel level) {
        return getByDimension(level.dimension().location())
            .map(instance -> instance.definition().allowNaturalMobSpawning())
            .orElse(true);
    }

    public void tryActivateReturnPortal(ServerPlayer player) {
        if (player.isOnPortalCooldown()) return;
        getByDimension(player.level().dimension().location())
            .flatMap(DungeonInstance::plan)
            .ifPresent(plan -> {
                BlockPos portalPos = DestinationPortalPlacement.position(
                    plan, ServerConfig.INSTANCE.destinationPortalBehindEntryBlocks.get());
                if (player.serverLevel().getBlockEntity(portalPos) instanceof ManifestationPortalBlockEntity portal
                    && portal.endpoint() == ManifestationPortalBlockEntity.Endpoint.RETURN
                    && portal.intersects(player.getBoundingBox())) {
                    com.cappleapple.instancednotinfinite.content.ManifestationPortalBlock.tryActivate(
                        player.serverLevel(), portalPos, player);
                }
            });
    }

    public void recoverPlayer(ServerPlayer player) {
        this.returns.recoverOnLogin(player);
    }

    public void tick() {
        requireServerThread();
        processCleanupResults();
        if (++this.tickCounter % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (DungeonInstance instance : List.copyOf(this.data.values())) {
            if ((instance.state() == InstanceState.ACTIVE || instance.state() == InstanceState.VACANT)
                && instance.lifecycleSettings().forceCollapseExpired(instance.openedAtMillis(), now)) {
                InstancedNotInfinite.LOGGER.info(
                    "[Dungeon {}] Forced collapse deadline reached", instance.id().shortId());
                returnAll(instance);
                if (onlineInside(instance) == 0) beginUnloadQuietly(instance);
                continue;
            }
            switch (instance.state()) {
                case ACTIVE -> {
                    enforceRadius(instance);
                    if (onlineInside(instance) == 0) {
                        instance.transition(InstanceState.VACANT, now);
                        this.data.changed();
                        InstancedNotInfinite.LOGGER.info("[Dungeon {}] VACANT", instance.id().shortId());
                    }
                }
                case VACANT -> {
                    if (onlineInside(instance) > 0) {
                        instance.transition(InstanceState.ACTIVE, now);
                        this.data.changed();
                    } else if (InstanceVacancyPolicy.expired(
                        instance.vacantSinceMillis(), now, vacancyTimeoutSeconds(instance))) {
                        beginUnloadQuietly(instance);
                    }
                }
                case COMPLETED -> {
                    if (now - instance.completedAtMillis() >= ServerConfig.INSTANCE.completedExitDelaySeconds.get() * 1_000L) {
                        returnAll(instance);
                        if (onlineInside(instance) == 0) {
                            beginUnloadQuietly(instance);
                        }
                    }
                }
                case UNLOADING -> {
                    // Block changes can enqueue POI work back to the server thread. Keep the
                    // level alive across at least one complete tick so those tasks cannot
                    // outlive its SectionStorage. Tick-based grace also remains deterministic
                    // on an accelerated GameTest server.
                    int requestedAt = this.unloadRequestedAtTick.computeIfAbsent(instance.id(), ignored -> this.tickCounter);
                    if (this.tickCounter - requestedAt >= 2) {
                        finishUnloadQuietly(instance);
                    }
                }
                case FAILED -> beginUnloadQuietly(instance);
                case DELETE_PENDING -> requestCleanupIfDue(instance, now);
                default -> {
                }
            }
        }
    }

    public void requestCleanupRetries() {
        long now = System.currentTimeMillis();
        this.data.values().stream()
            .filter(instance -> instance.state() == InstanceState.DELETE_PENDING)
            .forEach(instance -> requestCleanupIfDue(instance, now));
    }

    public PortalCountdown portalCountdown(InstanceId id) {
        DungeonInstance instance = this.data.get(id).orElse(null);
        if (instance == null) return PortalCountdown.closing();
        PortalCountdown forced = countdown(
            instance.openedAtMillis(), instance.lifecycleSettings().forceCollapseSeconds());
        if (instance.state() == InstanceState.VACANT) {
            PortalCountdown vacant = countdown(
                instance.vacantSinceMillis(), vacancyTimeoutSeconds(instance));
            return earlier(vacant, forced);
        }
        if (instance.state() == InstanceState.ACTIVE) {
            return forced;
        }
        if (instance.state() == InstanceState.COMPLETED) {
            int completedTicks = ServerConfig.INSTANCE.completedExitDelaySeconds.get() * 20;
            int remaining = InstanceVacancyPolicy.remainingTicks(
                instance.completedAtMillis(), System.currentTimeMillis(), ServerConfig.INSTANCE.completedExitDelaySeconds.get());
            return new PortalCountdown(completedTicks, remaining, true);
        }
        return PortalCountdown.closing();
    }

    private static int vacancyTimeoutSeconds(DungeonInstance instance) {
        return instance.lifecycleSettings().vacancySeconds(instance.everEntered());
    }

    private static PortalCountdown countdown(long startedAtMillis, int timeoutSeconds) {
        if (timeoutSeconds == InstanceLifecycleSettings.INFINITE) {
            return new PortalCountdown(0, 0, false);
        }
        return new PortalCountdown(
            timeoutSeconds * 20,
            InstanceVacancyPolicy.remainingTicks(startedAtMillis, System.currentTimeMillis(), timeoutSeconds),
            true);
    }

    private static PortalCountdown earlier(PortalCountdown first, PortalCountdown second) {
        if (!first.countingDown()) return second;
        if (!second.countingDown()) return first;
        return first.remainingTicks() <= second.remainingTicks() ? first : second;
    }

    private static InstanceLifecycleSettings configuredLifecycle() {
        return new InstanceLifecycleSettings(
            ServerConfig.INSTANCE.vacancyTimeoutSeconds.get(),
            ServerConfig.INSTANCE.postVisitVacancyTimeoutSeconds.get(),
            ServerConfig.INSTANCE.forceCollapseTimeoutSeconds.get());
    }

    public record PortalCountdown(int totalTicks, int remainingTicks, boolean countingDown) {
        public PortalCountdown {
            if (totalTicks < 0 || remainingTicks < 0) {
                throw new IllegalArgumentException("Portal countdown ticks cannot be negative");
            }
        }

        public static PortalCountdown closing() {
            return new PortalCountdown(0, 0, true);
        }
    }

    public void rebuildCatalogue() {
        requireServerThread();
        DungeonDefinitionRegistry.INSTANCE.rebuildAutomatic(this.server);
        com.cappleapple.instancednotinfinite.recipe.PortalRecipeGenerationService.INSTANCE.rebuild(
            this.server.registryAccess(), this.server.getResourceManager(), this.server.getRecipeManager(),
            this.server.getStructureManager(),
            com.cappleapple.instancednotinfinite.recipe.ItemTagLookups.bound(this.server.registryAccess()),
            this.server.getWorldData().worldGenOptions().seed());
        if (!this.server.getPlayerList().getPlayers().isEmpty()) {
            var packet = new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(
                this.server.getRecipeManager().getOrderedRecipes());
            this.server.getPlayerList().getPlayers().forEach(player -> player.connection.send(packet));
        }
        com.cappleapple.instancednotinfinite.network.ManifestationNetwork.broadcastDungeonCatalog();
    }

    private void recover() {
        long now = System.currentTimeMillis();
        InstancedNotInfinite.LOGGER.info("Recovering {} persisted dungeon instance records", this.data.values().size());
        for (DungeonInstance instance : List.copyOf(this.data.values())) {
            try {
                switch (instance.state()) {
                    case ACTIVE, VACANT -> restore(instance);
                    case DELETE_PENDING -> requestCleanupIfDue(instance, now);
                    case DELETED -> this.data.remove(instance.id());
                    case COMPLETED -> {
                        instance.transition(InstanceState.UNLOADING, now);
                        instance.transition(InstanceState.DELETE_PENDING, now);
                        this.data.changed();
                        requestCleanupIfDue(instance, now);
                    }
                    case UNLOADING -> {
                        instance.transition(InstanceState.DELETE_PENDING, now);
                        this.data.changed();
                        requestCleanupIfDue(instance, now);
                    }
                    case CREATING -> {
                        instance.fail("Server stopped during creation; conservative startup cleanup", now);
                        instance.transition(InstanceState.DELETE_PENDING, now);
                        this.data.changed();
                        requestCleanupIfDue(instance, now);
                    }
                    case FAILED -> {
                        instance.transition(InstanceState.DELETE_PENDING, now);
                        this.data.changed();
                        requestCleanupIfDue(instance, now);
                    }
                }
            } catch (Exception exception) {
                InstancedNotInfinite.LOGGER.error("[Dungeon {}] Recovery failed; retaining record", instance.id().shortId(), exception);
                if (instance.state() != InstanceState.FAILED && InstanceStateMachine.canTransition(instance.state(), InstanceState.FAILED)) {
                    instance.fail("Recovery failed: " + exception.getMessage(), now);
                    this.data.changed();
                }
            }
        }
    }

    private void restore(DungeonInstance instance) throws Exception {
        GenerationPlan plan = instance.plan().orElseThrow(() -> new IllegalStateException("Persisted active instance has no generation plan"));
        Registry<Biome> biomes = this.server.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> biome = biomes.getHolder(ResourceKey.create(Registries.BIOME, instance.biomeId()))
            .orElseThrow(() -> new IllegalStateException("Persisted biome is unavailable: " + instance.biomeId()));
        ResolvedDungeonDefinition resolved = new ResolvedDungeonDefinition(
            instance.definition(), instance.structureId(), instance.structureKind(), biome, instance.biomeId());
        DynamicLevelBackend.CreatedLevel created = this.backend.create(this.server, instance.id(), resolved, instance.seed());
        created.generator().updatePlan(plan);
        resizeWorldBorder(created.level(), plan);
        ResolvedPortalColors portalColors = instance.portalColors().orElseGet(() ->
            PortalAppearanceResolver.configured(
                instance.definition(), java.util.OptionalInt.of(biome.value().getFogColor())));
        instance.setPortalColors(portalColors);
        placeReturnPortal(created.level(), plan, instance.id(), portalColors);
        InstancedNotInfinite.LOGGER.info("[Dungeon {}] Restored {} instance level", instance.id().shortId(), instance.state());
    }

    private void failCreation(DungeonInstance instance, Exception cause) {
        long now = System.currentTimeMillis();
        try {
            instance.fail(cause.getClass().getSimpleName() + ": " + cause.getMessage(), now);
            this.data.changed();
        } catch (Exception rollbackException) {
            cause.addSuppressed(rollbackException);
            this.data.changed();
        }
        InstancedNotInfinite.LOGGER.error("[Dungeon {}] Creation failed and rollback was queued", instance.id().shortId(), cause);
    }

    private void beginUnload(DungeonInstance instance) throws InstanceOperationException {
        long now = System.currentTimeMillis();
        try {
            if (!this.backend.isLoaded(this.server, levelKey(instance))) {
                if (instance.state() != InstanceState.DELETE_PENDING) {
                    instance.transition(InstanceState.DELETE_PENDING, now);
                }
                this.data.changed();
                requestCleanupIfDue(instance, now);
                return;
            }
            if (instance.state() != InstanceState.UNLOADING) {
                instance.transition(InstanceState.UNLOADING, now);
                NeoForge.EVENT_BUS.post(new DungeonInstanceDeletingEvent(instance));
            }
            this.unloadRequestedAtTick.putIfAbsent(instance.id(), this.tickCounter);
            this.data.changed();
        } catch (Exception exception) {
            if (InstanceStateMachine.canTransition(instance.state(), InstanceState.FAILED)) {
                instance.fail("Unload failed: " + exception.getMessage(), System.currentTimeMillis());
                this.data.changed();
            }
            throw new InstanceOperationException("Failed to unload instance " + instance.id().shortId(), exception);
        }
    }

    private void finishUnload(DungeonInstance instance) throws InstanceOperationException {
        try {
            this.backend.unload(this.server, levelKey(instance));
            long now = System.currentTimeMillis();
            instance.transition(InstanceState.DELETE_PENDING, now);
            this.unloadRequestedAtTick.remove(instance.id());
            this.data.changed();
            requestCleanupIfDue(instance, now);
        } catch (Exception exception) {
            if (InstanceStateMachine.canTransition(instance.state(), InstanceState.FAILED)) {
                instance.fail("Unload failed: " + exception.getMessage(), System.currentTimeMillis());
                this.data.changed();
            }
            throw new InstanceOperationException("Failed to unload instance " + instance.id().shortId(), exception);
        }
    }

    private void beginUnloadQuietly(DungeonInstance instance) {
        try {
            beginUnload(instance);
        } catch (InstanceOperationException exception) {
            InstancedNotInfinite.LOGGER.error("[Dungeon {}] Automatic unload failed", instance.id().shortId(), exception);
        }
    }

    private void finishUnloadQuietly(DungeonInstance instance) {
        try {
            finishUnload(instance);
        } catch (InstanceOperationException exception) {
            InstancedNotInfinite.LOGGER.error("[Dungeon {}] Deferred unload failed", instance.id().shortId(), exception);
        }
    }

    private void requestCleanupIfDue(DungeonInstance instance, long now) {
        if (instance.state() != InstanceState.DELETE_PENDING || this.cleanup.inFlight(instance.id())) {
            return;
        }
        long retry = ServerConfig.INSTANCE.cleanupRetrySeconds.get() * 1_000L;
        if (instance.lastCleanupAttemptMillis() != 0L && now - instance.lastCleanupAttemptMillis() < retry) {
            return;
        }
        if (this.backend.isLoaded(this.server, levelKey(instance))) {
            InstancedNotInfinite.LOGGER.error("[Dungeon {}] Refusing deletion because the level remains loaded", instance.id().shortId());
            return;
        }
        instance.markCleanupAttempt(now);
        this.data.changed();
        Path path = this.backend.storagePath(this.server, levelKey(instance));
        this.cleanup.request(this.server, instance.id(), path);
    }

    private void processCleanupResults() {
        InstanceCleanupManager.Result result;
        while ((result = this.cleanup.poll()) != null) {
            DungeonInstance instance = this.data.get(result.id()).orElse(null);
            if (instance == null) {
                continue;
            }
            if (result.success()) {
                if (instance.state() == InstanceState.DELETE_PENDING) {
                    instance.transition(InstanceState.DELETED, System.currentTimeMillis());
                }
                this.data.remove(instance.id());
                InstancedNotInfinite.LOGGER.info("[Dungeon {}] Instance data deleted", instance.id().shortId());
            } else {
                InstancedNotInfinite.LOGGER.error(
                    "[Dungeon {}] Instance deletion failed; record remains DELETE_PENDING and will retry",
                    instance.id().shortId(), result.failure());
            }
        }
    }

    private void returnAll(DungeonInstance instance) {
        ServerLevel level = this.server.getLevel(levelKey(instance));
        if (level == null) {
            return;
        }
        for (ServerPlayer player : List.copyOf(level.players())) {
            this.returns.returnPlayer(player);
        }
    }

    private int onlineInside(DungeonInstance instance) {
        ServerLevel level = this.server.getLevel(levelKey(instance));
        return level == null ? 0 : level.players().size();
    }

    private void enforceRadius(DungeonInstance instance) {
        ServerLevel level = this.server.getLevel(levelKey(instance));
        GenerationPlan plan = instance.plan().orElse(null);
        if (level == null || plan == null) {
            return;
        }
        int radius = instance.definition().terrain().maximumRadius() - 4;
        for (ServerPlayer player : level.players()) {
            BlockPos position = player.blockPosition();
            if (HorizontalRadiusGuard.isOutside(position.getX(), position.getZ(), radius)) {
                BlockPos entry = plan.entryPosition();
                player.teleportTo(level, entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5, player.getYRot(), player.getXRot());
            }
        }
    }

    private Optional<DungeonInstance> getByDimension(ResourceLocation dimension) {
        return this.data.values().stream().filter(instance -> instance.dimensionId().equals(dimension)).findFirst();
    }

    private DungeonInstance require(InstanceId id) throws InstanceOperationException {
        return this.data.get(id).orElseThrow(() -> new InstanceOperationException("Unknown instance " + id));
    }

    private static ResourceKey<Level> levelKey(DungeonInstance instance) {
        return ResourceKey.create(Registries.DIMENSION, instance.dimensionId());
    }

    private static void resizeWorldBorder(ServerLevel level, GenerationPlan plan) {
        BoundingBox bounds = plan.envelopeBounds();
        int radius = Math.max(
            Math.max(Math.abs(bounds.minX()), Math.abs(bounds.maxX())),
            Math.max(Math.abs(bounds.minZ()), Math.abs(bounds.maxZ())));
        level.getWorldBorder().setCenter(0.0, 0.0);
        level.getWorldBorder().setSize(Math.max(2.0, (radius + 1) * 2.0));
    }

    private static DungeonDefinition effectiveDefinition(DungeonDefinition source, boolean automaticDefinition) {
        int cappedRadius = Math.min(source.terrain().maximumRadius(), ServerConfig.INSTANCE.maximumTerrainRadius.get());
        int horizontalPadding = source.terrain().horizontalPadding();
        if ((automaticDefinition && GenerationPlan.usesSurfaceApproach(source.environment()))
            || GenerationPlan.usesUndergroundApproach(source.environment())) {
            int approachHalfWidth = Math.max(
                ServerConfig.INSTANCE.approachPlatformRadius.get(),
                Math.max(ServerConfig.INSTANCE.approachPathWidth.get() / 2,
                    ServerConfig.INSTANCE.destinationPortalBehindEntryBlocks.get()));
            horizontalPadding = Math.max(
                horizontalPadding,
                ServerConfig.INSTANCE.approachDistance.get() + approachHalfWidth + 1);
        }
        TerrainSettings terrain = new TerrainSettings(
            horizontalPadding, source.terrain().verticalPadding(), cappedRadius);
        return new DungeonDefinition(
            source.id(), source.formatVersion(), source.structure(), source.structureKind(), source.weight(), source.biomes(),
            source.height(), source.environment(), source.customEnvironment(), terrain, source.portal(), source.entry(), source.placement(),
            source.decoration(), source.allowNaturalMobSpawning() && ServerConfig.INSTANCE.allowNaturalMobSpawning.get(), source.reentry());
    }

    private static GenerationPlan withSafeEntry(
        ServerLevel level,
        GenerationPlan plan,
        boolean automaticDefinition,
        PreparedStructure prepared
    ) throws InstanceOperationException {
        BlockPos requested = plan.entryPosition();
        BlockPos feet;
        float yaw = plan.entryYaw();
        float requestedYaw = yaw;
        if (automaticDefinition && GenerationPlan.usesSurfaceApproach(plan.definition().environment())) {
            AutomaticEntryLocator.Approach approach = AutomaticEntryLocator.locate(level, plan)
                .orElseThrow(() -> new InstanceOperationException(
                    "Generated surface structure has no detectable exterior side around its actual bounds " + plan.structureBounds()));
            AutomaticApproachBuilder.BuiltApproach built = AutomaticApproachBuilder.build(level, plan, approach);
            feet = built.spawn();
            yaw = built.yaw();
            if (!isSafeEntryAndReturnPortal(level, feet, yaw)) {
                throw new InstanceOperationException("Configured automatic approach did not create a safe player platform at " + feet);
            }
        } else if (GenerationPlan.usesUndergroundApproach(plan.definition().environment())) {
            AutomaticApproachBuilder.Settings settings = AutomaticApproachBuilder.Settings.fromConfig();
            AutomaticEntryLocator.Approach approach = UndergroundEntryLocator.locate(
                    level, plan, prepared.authoredPieceBounds(), settings)
                .orElseThrow(() -> new InstanceOperationException(
                    "Generated underground structure has no walkable authored interior with a route into its stone encasement"));
            AutomaticApproachBuilder.BuiltApproach built = AutomaticApproachBuilder.build(level, plan, approach, settings);
            feet = built.spawn();
            yaw = built.yaw();
            if (!isSafeEntryAndReturnPortal(level, feet, yaw)) {
                throw new InstanceOperationException("Configured underground approach did not create a safe player platform at " + feet);
            }
        } else {
            feet = (automaticDefinition
                    ? SafeEntrySearch.automatic(plan, candidate -> isSafeEntryAndReturnPortal(level, candidate, requestedYaw))
                    : SafeEntrySearch.nearby(plan, candidate -> isSafeEntryAndReturnPortal(level, candidate, requestedYaw)))
                .orElseThrow(() -> new InstanceOperationException(automaticDefinition
                    ? "Generated structure has no safe standing space inside its actual bounds " + plan.structureBounds()
                    : "Configured entry has no safe standing space within 12 horizontal and 32 vertical blocks of " + requested));
        }
        return new GenerationPlan(
            plan.seed(), plan.definition(), plan.structureBounds(), plan.guaranteedBounds(), plan.envelopeBounds(),
            plan.structureOrigin(), plan.terrainSurfaceY(), feet.immutable(), yaw);
    }

    private static boolean isSafeStandingPosition(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 1 >= level.getMaxBuildHeight()) {
            return false;
        }
        VoxelShape feetShape = level.getBlockState(feet).getCollisionShape(level, feet);
        VoxelShape headShape = level.getBlockState(feet.above()).getCollisionShape(level, feet.above());
        VoxelShape floorShape = level.getBlockState(feet.below()).getCollisionShape(level, feet.below());
        return feetShape.isEmpty() && headShape.isEmpty() && !floorShape.isEmpty()
            && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty();
    }

    private static boolean isSafeEntryAndReturnPortal(ServerLevel level, BlockPos entry, float yaw) {
        BlockPos portal = DestinationPortalPlacement.position(
            entry, yaw, ServerConfig.INSTANCE.destinationPortalBehindEntryBlocks.get());
        return isSafeStandingPosition(level, entry) && isSafeStandingPosition(level, portal);
    }

    private static void placeReturnPortal(
        ServerLevel level,
        GenerationPlan plan,
        InstanceId id,
        ResolvedPortalColors portalColors
    )
        throws InstanceOperationException {
        BlockPos portalPos = DestinationPortalPlacement.position(
            plan, ServerConfig.INSTANCE.destinationPortalBehindEntryBlocks.get());
        if (!level.getBlockState(portalPos).is(ModContent.MANIFESTATION_PORTAL.get())
            && !level.getBlockState(portalPos).canBeReplaced()) {
            throw new InstanceOperationException("Return portal position is not replaceable at " + portalPos.toShortString());
        }
        level.setBlock(portalPos, ModContent.MANIFESTATION_PORTAL.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(portalPos) instanceof ManifestationPortalBlockEntity portal)) {
            throw new InstanceOperationException("Return portal block entity was not created at " + portalPos.toShortString());
        }
        portal.bindReturn(
            id.value(), ResourceLocation.parse(plan.definition().id()), Math.round(plan.entryYaw()),
            ServerConfig.INSTANCE.portalWidth.get().floatValue(), ServerConfig.INSTANCE.portalHeight.get().floatValue(),
            ServerConfig.INSTANCE.portalDepth.get().floatValue(),
            ServerConfig.INSTANCE.portalMinimumWidth.get().floatValue(), ServerConfig.INSTANCE.portalMinimumHeight.get().floatValue(),
            ServerConfig.INSTANCE.portalMinimumDepth.get().floatValue(),
            portalColors.innerColor(), portalColors.outerColor());
    }

    private void requireServerThread() {
        if (!this.server.isSameThread()) {
            throw new IllegalStateException("Dungeon lifecycle operations must run on the Minecraft server thread");
        }
    }

    @Override
    public void close() {
        this.data.changed();
        this.server.overworld().getDataStorage().save();
        this.cleanup.close();
        if (active == this) {
            active = null;
        }
    }
}
