package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.manifestation.PortalAppearanceResolver;
import com.cappleapple.instancednotinfinite.manifestation.PortalColor;
import com.cappleapple.instancednotinfinite.manifestation.ResolvedPortalColors;
import com.cappleapple.instancednotinfinite.snapshot.DungeonVisualSnapshot;
import com.cappleapple.instancednotinfinite.snapshot.DungeonVisualSnapshotBuilder;
import com.cappleapple.instancednotinfinite.snapshot.VisualBlock;
import com.cappleapple.instancednotinfinite.structure.FloatingTerrainRemoval;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Server-thread-only, resumable dungeon construction job. */
public final class DungeonGenerationJob {
    private static final int ESTIMATED_OPERATIONS_PER_CHUNK = 4096;
    private static final TicketType<java.util.UUID> GENERATION_TICKET = TicketType.create(
        "instancednotinfinite_generation", java.util.UUID::compareTo);

    private final DungeonInstanceManager manager;
    private PreparedDungeonCreation creation;
    private final List<ChunkPos> terrainChunks;
    private final List<ChunkPos> structureChunks;
    private final DungeonVisualSnapshotBuilder snapshot;
    private final boolean presentationSnapshot;
    private final Consumer<List<VisualBlock>> batchConsumer;
    private int terrainIndex;
    private int heightmapIndex;
    private int structureIndex;
    private final FloatingTerrainRemoval floatingRemoval;
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
        PreparedDungeonCreation creation,
        int maximumSnapshotBlocks,
        boolean presentationEnvelope,
        Consumer<List<VisualBlock>> batchConsumer
    ) {
        this.manager = manager;
        this.creation = creation;
        // Holograms are structure-only. Prime only the structure and entry chunks up front;
        // the bounded controlled terrain around them remains lazy when a player enters.
        this.terrainChunks = chunksFor(
            creation.plan().structureBounds(), creation.plan().entryPosition().getX(), creation.plan().entryPosition().getZ());
        this.structureChunks = chunksFor(creation.structure().bounds(), null, null);
        this.presentationSnapshot = presentationEnvelope;
        this.snapshot = new DungeonVisualSnapshotBuilder(
            creation.instance(), creation.plan(), maximumSnapshotBlocks, presentationEnvelope);
        this.batchConsumer = batchConsumer;
        this.floatingRemoval = creation.plan().floatingVoid()
            ? new FloatingTerrainRemoval(creation.created().level(), creation.plan()) : null;
        if (this.floatingRemoval != null) creation.created().generator().beginFloatingTerrain();
        this.structureChunks.forEach(chunk -> creation.created().level().getChunkSource().addRegionTicket(
            GENERATION_TICKET, chunk, 0, creation.instance().id().value()));
    }

    /** Advances complete chunk-sized work units until the time budget or hard cap is reached. */
    public void advance(double timeBudgetMillis, int operationCap) throws InstanceOperationException {
        if (this.complete) {
            return;
        }
        long start = System.nanoTime();
        long budget = Math.max(1L, (long)(timeBudgetMillis * 1_000_000.0));
        int operations = 0;
        try {
            do {
                if (this.terrainIndex < this.terrainChunks.size()) {
                    ChunkPos chunk = this.terrainChunks.get(this.terrainIndex++);
                    this.manager.structurePlacer().generateTerrainChunk(this.creation.created().level(), chunk.x, chunk.z);
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
            this.manager.failPreparedCreation(this.creation, exception);
            throw new InstanceOperationException(
                "Dungeon instance " + this.creation.instance().id().shortId() + " failed: " + exception.getMessage(), exception);
        }
    }

    public double progress() {
        int total = this.terrainChunks.size() + this.structureChunks.size() * 2 + 1
            + (this.floatingRemoval == null ? 0 : this.cleanupChunks == null ? this.terrainChunks.size() : this.cleanupChunks.size());
        int done = this.terrainIndex + this.heightmapIndex + this.structureIndex + this.cleanupIndex + (this.complete ? 1 : 0);
        return Math.min(1.0, done / (double)total);
    }

    public boolean complete() {
        return this.complete;
    }

    public DungeonInstance instance() {
        return this.creation.instance();
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
        return this.creation.biomeFogColor();
    }

    public BoundingBox visualBounds() {
        BoundingBox envelope = this.creation.plan().envelopeBounds();
        BoundingBox structure = this.creation.plan().structureBounds();
        return new BoundingBox(
            structure.minX() - envelope.minX(), structure.minY() - envelope.minY(), structure.minZ() - envelope.minZ(),
            structure.maxX() - envelope.minX(), structure.maxY() - envelope.minY(), structure.maxZ() - envelope.minZ());
    }

    public void releaseTickets() {
        if (this.ticketsReleased) return;
        this.ticketsReleased = true;
        this.structureChunks.forEach(chunk -> this.creation.created().level().getChunkSource().removeRegionTicket(
            GENERATION_TICKET, chunk, 0, this.creation.instance().id().value()));
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
