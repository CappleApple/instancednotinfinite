package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.manifestation.AnimationMode;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationScoreMath;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationState;
import com.cappleapple.instancednotinfinite.manifestation.PreparationParticleStyle;
import com.cappleapple.instancednotinfinite.network.ManifestationBlocksPayload;
import com.cappleapple.instancednotinfinite.network.ManifestationProgressPayload;
import com.cappleapple.instancednotinfinite.network.ManifestationStartPayload;
import com.cappleapple.instancednotinfinite.snapshot.VisualLayer;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;

public final class ClientManifestation {
    private final UUID id;
    private ResourceLocation dimension;
    private BlockPos origin;
    private int rotationDegrees;
    private UUID instanceId;
    private ResourceLocation dungeonId;
    private long animationSeed;
    private AnimationMode animationMode;
    private ManifestationState state;
    private float generationProgress;
    private float animationProgress;
    private float smoothedProgress;
    private long stateChangedGameTime;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private int scoreMinX;
    private int scoreMinY;
    private int scoreMinZ;
    private int scoreMaxX;
    private int scoreMaxY;
    private int scoreMaxZ;
    private double scoreFloor = Double.NaN;
    private float maximumWidth;
    private float maximumHeight;
    private float maximumDepth;
    private float terrainAlpha;
    private float structureAlpha;
    private int collapseDurationTicks;
    private int portalGrowthDurationTicks;
    private int portalCloseDurationTicks;
    private float portalWidth;
    private float portalHeight;
    private float portalDepth;
    private float portalMinimumWidth;
    private float portalMinimumHeight;
    private float portalMinimumDepth;
    private int portalInnerColor;
    private int portalOuterColor;
    private int portalCountdownTotalTicks;
    private int portalCountdownRemainingTicks;
    private boolean portalCountdownActive;
    private PreparationParticleStyle preparationParticleStyle;
    private int preparationParticleColor;
    private int preparationParticleRate;
    private float preparationParticleScale;
    private float preparationParticleRadius;
    private int visualMinX = Integer.MAX_VALUE;
    private int visualMinY = Integer.MAX_VALUE;
    private int visualMinZ = Integer.MAX_VALUE;
    private int visualMaxX = Integer.MIN_VALUE;
    private int visualMaxY = Integer.MIN_VALUE;
    private int visualMaxZ = Integer.MIN_VALUE;
    private final Map<Long, ClientVisualBlock> byPosition = new LinkedHashMap<>();
    private int visualRevision;
    private int ticksSinceBlockUpdate;

    ClientManifestation(ManifestationStartPayload payload) {
        this.id = payload.id();
        update(payload);
        this.smoothedProgress = payload.animationProgress();
    }

    private ClientManifestation(UUID id, ResourceLocation dungeonId, List<ClientVisualBlock> blocks) {
        this.id = id;
        this.dimension = Level.OVERWORLD.location();
        this.origin = BlockPos.ZERO;
        this.rotationDegrees = 180;
        this.instanceId = id;
        this.dungeonId = dungeonId;
        this.animationMode = AnimationMode.NONE;
        this.state = ManifestationState.COMPLETE;
        this.generationProgress = 1.0F;
        this.animationProgress = 1.0F;
        this.smoothedProgress = 1.0F;
        this.maximumWidth = 1.0F;
        this.maximumHeight = 1.0F;
        this.maximumDepth = 1.0F;
        this.structureAlpha = 1.0F;
        this.preparationParticleStyle = PreparationParticleStyle.NONE;
        for (ClientVisualBlock block : blocks) {
            if (block.state().isAir() || block.layer() != VisualLayer.STRUCTURE) continue;
            this.byPosition.put(block.position().asLong(), block);
            includeVisualPosition(block.position());
        }
        this.sizeX = visualSizeX();
        this.sizeY = visualSizeY();
        this.sizeZ = visualSizeZ();
        this.scoreMinX = this.visualMinX;
        this.scoreMinY = this.visualMinY;
        this.scoreMinZ = this.visualMinZ;
        this.scoreMaxX = this.visualMaxX;
        this.scoreMaxY = this.visualMaxY;
        this.scoreMaxZ = this.visualMaxZ;
        this.visualRevision = this.byPosition.isEmpty() ? 0 : 1;
        this.ticksSinceBlockUpdate = Integer.MAX_VALUE;
    }

    static ClientManifestation persistedMiniature(UUID id, ResourceLocation dungeonId, List<ClientVisualBlock> blocks) {
        return new ClientManifestation(id, dungeonId, blocks);
    }

    void update(ManifestationStartPayload payload) {
        this.dimension = payload.dimension();
        this.origin = payload.origin();
        this.rotationDegrees = payload.rotationDegrees();
        this.instanceId = payload.instanceId();
        this.dungeonId = payload.dungeonId();
        this.animationSeed = payload.animationSeed();
        this.animationMode = payload.animationMode();
        this.state = payload.state();
        this.generationProgress = payload.generationProgress();
        this.animationProgress = payload.animationProgress();
        this.stateChangedGameTime = payload.stateChangedGameTime();
        this.sizeX = Math.max(1, payload.sizeX());
        this.sizeY = Math.max(1, payload.sizeY());
        this.sizeZ = Math.max(1, payload.sizeZ());
        this.scoreMinX = payload.visualMinX();
        this.scoreMinY = payload.visualMinY();
        this.scoreMinZ = payload.visualMinZ();
        this.scoreMaxX = Math.max(scoreMinX, payload.visualMaxX());
        this.scoreMaxY = Math.max(scoreMinY, payload.visualMaxY());
        this.scoreMaxZ = Math.max(scoreMinZ, payload.visualMaxZ());
        this.maximumWidth = payload.maximumWidth();
        this.maximumHeight = payload.maximumHeight();
        this.maximumDepth = payload.maximumDepth();
        this.terrainAlpha = payload.terrainAlpha();
        this.structureAlpha = payload.structureAlpha();
        this.collapseDurationTicks = payload.collapseDurationTicks();
        this.portalGrowthDurationTicks = payload.portalGrowthDurationTicks();
        this.portalCloseDurationTicks = payload.portalCloseDurationTicks();
        this.portalWidth = payload.portalWidth();
        this.portalHeight = payload.portalHeight();
        this.portalDepth = payload.portalDepth();
        this.portalMinimumWidth = payload.portalMinimumWidth();
        this.portalMinimumHeight = payload.portalMinimumHeight();
        this.portalMinimumDepth = payload.portalMinimumDepth();
        this.portalInnerColor = payload.portalInnerColor();
        this.portalOuterColor = payload.portalOuterColor();
        this.preparationParticleStyle = payload.preparationParticleStyle();
        this.preparationParticleColor = payload.preparationParticleColor();
        this.preparationParticleRate = payload.preparationParticleRate();
        this.preparationParticleScale = payload.preparationParticleScale();
        this.preparationParticleRadius = payload.preparationParticleRadius();
    }

    void add(ManifestationBlocksPayload payload) {
        List<ClientVisualBlock> incoming = new ArrayList<>(payload.blocks().size());
        double batchMinimum = Double.POSITIVE_INFINITY;
        for (ManifestationBlocksPayload.Entry entry : payload.blocks()) {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(entry.blockStateId());
            if (state == null || state.isAir()) continue;
            BlockPos position = entry.position();
            double rawScore = ManifestationScoreMath.score(
                position.getX(), position.getY(), position.getZ(),
                scoreMinX, scoreMinY, scoreMinZ, scoreMaxX, scoreMaxY, scoreMaxZ,
                animationMode, animationSeed);
            incoming.add(new ClientVisualBlock(position, state, entry.layer(), rawScore));
            if (entry.layer() == VisualLayer.STRUCTURE) batchMinimum = Math.min(batchMinimum, rawScore);
        }
        if (Double.isNaN(scoreFloor) && Double.isFinite(batchMinimum)) scoreFloor = batchMinimum;

        boolean changed = false;
        for (ClientVisualBlock raw : incoming) {
            double score = ManifestationScoreMath.normalizeRevealScore(
                raw.score(), Double.isNaN(scoreFloor) ? 0.0 : scoreFloor);
            ClientVisualBlock block = new ClientVisualBlock(raw.position(), raw.state(), raw.layer(), score);
            ClientVisualBlock previous = byPosition.put(block.position().asLong(), block);
            changed |= !block.equals(previous);
            if (block.layer() == VisualLayer.STRUCTURE) includeVisualPosition(block.position());
        }
        if (changed) {
            visualRevision++;
            ticksSinceBlockUpdate = 0;
        }
    }

    void update(ManifestationProgressPayload payload) {
        this.state = payload.state();
        this.generationProgress = payload.generationProgress();
        this.animationProgress = payload.animationProgress();
        this.stateChangedGameTime = payload.stateChangedGameTime();
        this.portalInnerColor = payload.portalInnerColor();
        this.portalOuterColor = payload.portalOuterColor();
        this.portalCountdownTotalTicks = payload.portalCountdownTotalTicks();
        this.portalCountdownRemainingTicks = payload.portalCountdownRemainingTicks();
        this.portalCountdownActive = payload.portalCountdownActive();
    }

    void tick() {
        float target = Math.min(generationProgress, animationProgress);
        smoothedProgress += (target - smoothedProgress) * 0.25F;
        if (Math.abs(target - smoothedProgress) < 0.001F) smoothedProgress = target;
        if (ticksSinceBlockUpdate < Integer.MAX_VALUE) ticksSinceBlockUpdate++;
        if (portalCountdownActive && portalCountdownRemainingTicks > 0) portalCountdownRemainingTicks--;
    }

    public UUID id() { return id; }
    public ResourceLocation dimension() { return dimension; }
    public BlockPos origin() { return origin; }
    public int rotationDegrees() { return rotationDegrees; }
    public UUID instanceId() { return instanceId; }
    public ResourceLocation dungeonId() { return dungeonId; }
    public ManifestationState state() { return state; }
    public float progress() { return smoothedProgress; }
    /** Raw client animation clock, intentionally independent of generation progress. */
    public float animationProgress() { return Math.max(0.0F, Math.min(1.0F, animationProgress)); }
    public long stateChangedGameTime() { return stateChangedGameTime; }
    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }
    public float maximumWidth() { return maximumWidth; }
    public float maximumHeight() { return maximumHeight; }
    public float maximumDepth() { return maximumDepth; }
    public float terrainAlpha() { return terrainAlpha; }
    public float structureAlpha() { return structureAlpha; }
    public int collapseDurationTicks() { return collapseDurationTicks; }
    public int portalGrowthDurationTicks() { return portalGrowthDurationTicks; }
    public int portalCloseDurationTicks() { return portalCloseDurationTicks; }
    public float portalWidth() { return portalWidth; }
    public float portalHeight() { return portalHeight; }
    public float portalDepth() { return portalDepth; }
    public float portalMinimumWidth() { return portalMinimumWidth; }
    public float portalMinimumHeight() { return portalMinimumHeight; }
    public float portalMinimumDepth() { return portalMinimumDepth; }
    public int portalInnerColor() { return portalInnerColor; }
    public int portalOuterColor() { return portalOuterColor; }
    public int portalCountdownRemainingTicks() { return portalCountdownRemainingTicks; }
    public boolean portalCountdownActive() { return portalCountdownActive; }
    public float portalLifetimeFraction() {
        if (!portalCountdownActive) return 1.0F;
        if (portalCountdownTotalTicks <= 0) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, portalCountdownRemainingTicks / (float)portalCountdownTotalTicks));
    }
    public PreparationParticleStyle preparationParticleStyle() { return preparationParticleStyle; }
    public int preparationParticleColor() { return preparationParticleColor; }
    public int preparationParticleRate() { return preparationParticleRate; }
    public float preparationParticleScale() { return preparationParticleScale; }
    public float preparationParticleRadius() { return preparationParticleRadius; }
    public List<ClientVisualBlock> blocks() { return List.copyOf(byPosition.values()); }
    int blockCount() { return byPosition.size(); }
    int visualRevision() { return visualRevision; }
    int ticksSinceBlockUpdate() { return ticksSinceBlockUpdate; }
    boolean generationComplete() { return generationProgress >= 0.9999F; }
    List<ClientVisualBlock> snapshotBlocks() { return List.copyOf(byPosition.values()); }
    public int visualSizeX() { return hasVisualBounds() ? visualMaxX - visualMinX + 1 : sizeX; }
    public int visualSizeY() { return hasVisualBounds() ? visualMaxY - visualMinY + 1 : sizeY; }
    public int visualSizeZ() { return hasVisualBounds() ? visualMaxZ - visualMinZ + 1 : sizeZ; }
    public double visualCenterX() { return hasVisualBounds() ? (visualMinX + visualMaxX + 1) * 0.5 : sizeX * 0.5; }
    public double visualCenterY() { return hasVisualBounds() ? (visualMinY + visualMaxY + 1) * 0.5 : sizeY * 0.5; }
    public double visualCenterZ() { return hasVisualBounds() ? (visualMinZ + visualMaxZ + 1) * 0.5 : sizeZ * 0.5; }

    private void includeVisualPosition(BlockPos position) {
        visualMinX = Math.min(visualMinX, position.getX());
        visualMinY = Math.min(visualMinY, position.getY());
        visualMinZ = Math.min(visualMinZ, position.getZ());
        visualMaxX = Math.max(visualMaxX, position.getX());
        visualMaxY = Math.max(visualMaxY, position.getY());
        visualMaxZ = Math.max(visualMaxZ, position.getZ());
    }

    private boolean hasVisualBounds() {
        return visualMinX != Integer.MAX_VALUE;
    }

    public record ClientVisualBlock(BlockPos position, BlockState state, VisualLayer layer, double score) {
    }
}
