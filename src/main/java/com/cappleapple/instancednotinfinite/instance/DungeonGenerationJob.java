package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.manifestation.PortalAppearanceResolver;
import com.cappleapple.instancednotinfinite.manifestation.PortalColor;
import com.cappleapple.instancednotinfinite.manifestation.ResolvedPortalColors;
import com.cappleapple.instancednotinfinite.mixin.ServerChunkCacheInvoker;
import com.cappleapple.instancednotinfinite.snapshot.DungeonVisualSnapshot;
import com.cappleapple.instancednotinfinite.snapshot.DungeonVisualSnapshotBuilder;
import com.cappleapple.instancednotinfinite.snapshot.VisualBlock;
import com.cappleapple.instancednotinfinite.structure.FloatingTerrainRemoval;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Server-thread coordinator for worker preparation, asynchronous terrain, and bounded placement. */
public final class DungeonGenerationJob {
    private static final int ESTIMATED_OPERATIONS_PER_CHUNK = 4096;
    private static final TicketType<java.util.UUID> GENERATION_TICKET = TicketType.create(
        "instancednotinfinite_generation", java.util.UUID::compareTo);

    private static final int TERRAIN_REQUEST_WINDOW = 4;
    private final DungeonInstanceManager manager;
    private final PendingDungeonCreation pending;
    private final int maximumSnapshotBlocks;
    private final Map<Integer, CompletableFuture<ChunkResult<ChunkAccess>>> terrainRequests = new HashMap<>();
    private final Set<ChunkPos> ticketedChunks = new LinkedHashSet<>();
    private int terrainRequestIndex;
    private PreparedDungeonCreation creation;
    private List<ChunkPos> terrainChunks = List.of();
    private List<ChunkPos> structureChunks = List.of();
    private DungeonVisualSnapshotBuilder snapshot;
    private final boolean presentationSnapshot;
    private final Consumer<List<VisualBlock>> batchConsumer;
    private int terrainIndex;
    private int heightmapIndex;
    private int structureIndex;
    private FloatingTerrainRemoval floatingRemoval;
    private List<ChunkPos> cleanupChunks;
    private int cleanupIndex;
    private boolean placementInitialized;
    private boolean placementConfirmed;
    private boolean complete;
    private boolean ticketsReleased;
    private DungeonVisualSnapshot completedSnapshot;
    private ResolvedPortalColors portalColors;

    DungeonGenerationJob(
        DungeonInstanceManager manager,
        PendingDungeonCreation pending,
        int maximumSnapshotBlocks,
        boolean presentationEnvelope,
        Consumer<List<VisualBlock>> batchConsumer
    ) {
        this.manager = manager;
        this.pending = pending;
        this.maximumSnapshotBlocks = maximumSnapshotBlocks;
        this.presentationSnapshot = presentationEnvelope;
        this.batchConsumer = batchConsumer;
        manager.registerGeneration(this);
    }

    private void initialize(PreparedDungeonCreation creation) {
        this.creation = creation;
        // Include a one-chunk halo for pieces that query neighboring heightmaps, plus the
        // requested entry. Request tickets gradually; never start the entire island at once.
        Set<ChunkPos> terrain = new LinkedHashSet<>(chunksFor(creation.plan().structureBounds().inflatedBy(16),
            creation.plan().entryPosition().getX(), creation.plan().entryPosition().getZ()));
        if (creation.structure().worldgenStart() != null) terrain.add(creation.structure().worldgenStart().getChunkPos());
        this.terrainChunks = List.copyOf(terrain);
        this.structureChunks = chunksFor(creation.structure().bounds(), null, null);
        this.snapshot = new DungeonVisualSnapshotBuilder(
            creation.instance(), creation.plan(), this.maximumSnapshotBlocks, this.presentationSnapshot);
        this.floatingRemoval = creation.plan().floatingVoid()
            ? new FloatingTerrainRemoval(creation.created().level(), creation.plan()) : null;
        if (this.floatingRemoval != null) creation.created().generator().beginFloatingTerrain();
    }

    /** Compatibility path for the explicitly synchronous Java creation API. */
    void advanceSynchronously() throws InstanceOperationException {
        advance(Double.MAX_VALUE, Integer.MAX_VALUE, true);
    }

    /** Advances complete chunk-sized work units until the time budget or hard cap is reached. */
    public void advance(double timeBudgetMillis, int operationCap) throws InstanceOperationException {
        advance(timeBudgetMillis, operationCap, false);
    }

    private void advance(double timeBudgetMillis, int operationCap, boolean synchronous) throws InstanceOperationException {
        if (!this.pending.created().level().getServer().isSameThread()) {
            throw new IllegalStateException("Dungeon generation must be coordinated on the server thread");
        }
        if (this.complete) {
            return;
        }
        if (this.ticketsReleased || instance().state() != InstanceState.CREATING) {
            throw new InstanceOperationException("Dungeon generation was cancelled");
        }
        long start = System.nanoTime();
        long budget = Math.max(1L, (long)(timeBudgetMillis * 1_000_000.0));
        int operations = 0;
        try {
            if (this.creation == null) {
                if (!synchronous && !this.pending.preparation().isDone()) return;
                initialize(this.manager.acceptPreparation(this.pending, this.pending.preparation().get()));
                // Give presentation callers a tick to publish the final coordinate frame
                // before any structure blocks are streamed.
                return;
            }
            do {
                if (this.terrainIndex < this.terrainChunks.size()) {
                    if (!synchronous && this.terrainRequestIndex < this.terrainChunks.size()
                        && this.terrainRequestIndex < this.terrainIndex + TERRAIN_REQUEST_WINDOW) {
                        ChunkPos requested = this.terrainChunks.get(this.terrainRequestIndex);
                        pin(requested);
                        var source = this.creation.created().level().getChunkSource();
                        this.terrainRequests.put(this.terrainRequestIndex++,
                            ((ServerChunkCacheInvoker)(Object)source).instancednotinfinite$requestChunk(
                                requested.x, requested.z, ChunkStatus.FULL, true));
                        operations += ESTIMATED_OPERATIONS_PER_CHUNK;
                        continue;
                    }
                    ChunkPos chunk = this.terrainChunks.get(this.terrainIndex);
                    if (synchronous) {
                        pin(chunk);
                        this.manager.structurePlacer().generateTerrainChunk(this.creation.created().level(), chunk.x, chunk.z);
                    } else {
                        var future = this.terrainRequests.get(this.terrainIndex);
                        if (!future.isDone()) return;
                        if (!future.join().isSuccess()) throw new IllegalStateException("Terrain chunk was unloaded: " + chunk);
                        this.terrainRequests.remove(this.terrainIndex);
                    }
                    this.terrainIndex++;
                    if (this.presentationSnapshot) {
                        List<VisualBlock> added = this.snapshot.captureChunk(this.creation.created().level(), chunk, false);
                        if (!added.isEmpty()) this.batchConsumer.accept(added);
                    }
                    operations += ESTIMATED_OPERATIONS_PER_CHUNK;
                } else if (this.heightmapIndex < this.structureChunks.size()) {
                    // Pieces such as igloos can query a neighboring intersecting chunk while
                    // the current piece is placed, so all structure-chunk heightmaps must be
                    // ready before the first piece runs.
                    ChunkPos chunk = this.structureChunks.get(this.heightmapIndex++);
                    this.manager.structurePlacer().primePlacementHeightmaps(
                        this.creation.created().level(), chunk.x, chunk.z);
                    operations += ESTIMATED_OPERATIONS_PER_CHUNK;
                } else if (this.structureIndex < this.structureChunks.size()) {
                    if (!this.placementInitialized) {
                        this.manager.structurePlacer().initializePlacement(this.creation.created().level(), this.creation.structure());
                        this.placementInitialized = true;
                    }
                    ChunkPos chunk = this.structureChunks.get(this.structureIndex++);
                    this.snapshot.beginStructureChunk(this.creation.created().level(), chunk);
                    try (var capture = this.floatingRemoval == null ? null : this.floatingRemoval.capture()) {
                        this.manager.structurePlacer().placeChunk(
                            this.creation.created().level(), this.creation.created().generator(), this.creation.structure(),
                            this.creation.instance().seed(), chunk.x, chunk.z);
                    }
                    List<VisualBlock> added = this.snapshot.captureChunk(this.creation.created().level(), chunk, true);
                    if (!added.isEmpty()) this.batchConsumer.accept(added);
                    operations += ESTIMATED_OPERATIONS_PER_CHUNK;
                } else if (!this.placementConfirmed) {
                    this.creation = this.manager.confirmPlacedEnvironment(this.creation);
                    this.placementConfirmed = true;
                    if (this.floatingRemoval != null) {
                        // Keep terrain for pieces that deferred ground projection until postProcess.
                        List<ChunkPos> temporary = this.creation.created().generator().finishFloatingTerrain();
                        this.cleanupChunks = this.creation.plan().floatingVoid() ? temporary : List.of();
                    }
                } else if (this.cleanupChunks != null && this.cleanupIndex < this.cleanupChunks.size()) {
                    this.floatingRemoval.clearChunk(this.cleanupChunks.get(this.cleanupIndex++));
                    operations += ESTIMATED_OPERATIONS_PER_CHUNK;
                } else {
                    if (this.floatingRemoval != null) this.floatingRemoval.release();
                    this.completedSnapshot = this.presentationSnapshot ? this.snapshot.build() : null;
                    this.portalColors = PortalAppearanceResolver.configured(
                        this.creation.instance().definition(), java.util.OptionalInt.of(this.creation.biomeFogColor()));
                    InstancedNotInfinite.LOGGER.info(
                        "[Dungeon {}] Portal color resolved from biome fog #{}; retained {} hologram blocks; inner={}, outer={}",
                        this.creation.instance().id().shortId(), String.format("%06X", this.creation.biomeFogColor() & 0x00FF_FFFF),
                        this.snapshot.retainedBlockCount(),
                        PortalColor.toRgbaHex(this.portalColors.innerColor()), PortalColor.toRgbaHex(this.portalColors.outerColor()));
                    this.manager.finishPreparedCreation(this.creation, this.portalColors);
                    this.complete = true;
                    releaseTickets();
                    return;
                }
            } while (operations < operationCap && System.nanoTime() - start < budget);
        } catch (Exception exception) {
            releaseTickets();
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            this.manager.failPreparedCreation(this.pending, exception);
            throw new InstanceOperationException(
                "Dungeon instance " + instance().id().shortId() + " failed: " + exception.getMessage(), exception);
        }
    }

    public boolean prepared() {
        return this.creation != null;
    }

    public double progress() {
        if (!prepared()) return 0.0;
        int total = this.terrainChunks.size() + this.structureChunks.size() * 2 + 1
            + (this.floatingRemoval == null ? 0 : this.cleanupChunks == null ? this.terrainChunks.size() : this.cleanupChunks.size());
        int done = this.terrainIndex + this.heightmapIndex + this.structureIndex + this.cleanupIndex + (this.complete ? 1 : 0);
        return GenerationProgress.fraction(done, total, this.complete);
    }

    public boolean complete() {
        return this.complete;
    }

    public DungeonInstance instance() {
        return this.pending.instance();
    }

    public Optional<DungeonVisualSnapshot> snapshot() {
        return Optional.ofNullable(this.completedSnapshot);
    }

    public DungeonVisualSnapshot currentSnapshot() {
        if (!this.presentationSnapshot) throw new IllegalStateException("Direct generation jobs do not build visual snapshots");
        return this.snapshot.build();
    }

    public ResolvedPortalColors portalColors() {
        if (this.portalColors == null) throw new IllegalStateException("Portal colors are not resolved until generation completes");
        return this.portalColors;
    }

    public int biomeFogColor() {
        return this.pending.biomeFogColor();
    }

    public BoundingBox visualBounds() {
        BoundingBox envelope = this.creation.plan().envelopeBounds();
        BoundingBox structure = this.creation.plan().structureBounds();
        return new BoundingBox(
            structure.minX() - envelope.minX(), structure.minY() - envelope.minY(), structure.minZ() - envelope.minZ(),
            structure.maxX() - envelope.minX(), structure.maxY() - envelope.minY(), structure.maxZ() - envelope.minZ());
    }

    private void pin(ChunkPos chunk) {
        if (this.ticketedChunks.add(chunk)) this.pending.created().level().getChunkSource().addRegionTicket(
            GENERATION_TICKET, chunk, 0, instance().id().value());
    }

    /** Idempotent cancellation as well as normal completion cleanup. Late worker results are discarded. */
    public void releaseTickets() {
        if (this.ticketsReleased) return;
        this.ticketsReleased = true;
        this.pending.preparation().cancel(true);
        this.terrainRequests.clear();
        this.ticketedChunks.forEach(chunk -> this.pending.created().level().getChunkSource().removeRegionTicket(
            GENERATION_TICKET, chunk, 0, instance().id().value()));
        this.ticketedChunks.clear();
        if (this.floatingRemoval != null) this.floatingRemoval.release();
        this.manager.releaseGeneration(this);
    }

    private static List<ChunkPos> chunksFor(BoundingBox bounds, Integer extraX, Integer extraZ) {
        Set<Long> packed = new LinkedHashSet<>();
        int minChunkX = SectionPos.blockToSectionCoord(bounds.minX());
        int maxChunkX = SectionPos.blockToSectionCoord(bounds.maxX());
        int minChunkZ = SectionPos.blockToSectionCoord(bounds.minZ());
        int maxChunkZ = SectionPos.blockToSectionCoord(bounds.maxZ());
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                packed.add(ChunkPos.asLong(chunkX, chunkZ));
            }
        }
        if (extraX != null && extraZ != null) {
            packed.add(ChunkPos.asLong(SectionPos.blockToSectionCoord(extraX), SectionPos.blockToSectionCoord(extraZ)));
        }
        List<ChunkPos> result = new ArrayList<>(packed.size());
        packed.forEach(value -> result.add(new ChunkPos(value)));
        return List.copyOf(result);
    }
}
