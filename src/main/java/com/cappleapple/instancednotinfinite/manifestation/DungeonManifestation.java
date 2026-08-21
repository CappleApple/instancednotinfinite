package com.cappleapple.instancednotinfinite.manifestation;

import com.cappleapple.instancednotinfinite.instance.InstanceId;
import com.cappleapple.instancednotinfinite.instance.InstanceLifecycleOverrides;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class DungeonManifestation {
    private final UUID id;
    private final ResourceLocation originDimension;
    private final BlockPos origin;
    private final int rotationDegrees;
    private final InstanceId instanceId;
    private final ResourceLocation dungeonId;
    private final DungeonTarget target;
    private final InstanceLifecycleOverrides lifecycleOverrides;
    private final long animationSeed;
    private final AnimationMode animationMode;
    private final UUID initiator;
    private final long createdAtMillis;
    private final long startedAtGameTime;
    private final int animationDurationTicks;
    private ManifestationState state;
    private long stateChangedAtGameTime;
    private double generationProgress;
    private double animationProgress;
    private String failureReason;
    private boolean itemConsumed;
    private CatalystConsumptionPolicy catalystConsumptionPolicy = CatalystConsumptionPolicy.NEVER;
    private boolean itemRefunded;
    private boolean animationForced;
    private ManifestationState closingOutcome = ManifestationState.COMPLETE;
    private ResolvedPortalColors portalColors;

    public DungeonManifestation(
        UUID id,
        ResourceLocation originDimension,
        BlockPos origin,
        int rotationDegrees,
        InstanceId instanceId,
        ResourceLocation dungeonId,
        DungeonTarget target,
        InstanceLifecycleOverrides lifecycleOverrides,
        long animationSeed,
        AnimationMode animationMode,
        UUID initiator,
        long createdAtMillis,
        long startedAtGameTime,
        int animationDurationTicks,
        ResolvedPortalColors portalColors
    ) {
        this.id = id;
        this.originDimension = originDimension;
        this.origin = origin.immutable();
        this.rotationDegrees = PortalRotation.normalize(rotationDegrees);
        this.instanceId = instanceId;
        this.dungeonId = dungeonId;
        this.target = target;
        this.lifecycleOverrides = lifecycleOverrides;
        this.animationSeed = animationSeed;
        this.animationMode = animationMode;
        this.initiator = initiator;
        this.createdAtMillis = createdAtMillis;
        this.startedAtGameTime = startedAtGameTime;
        this.animationDurationTicks = animationDurationTicks;
        this.portalColors = portalColors;
        this.state = ManifestationState.PREPARING;
        this.stateChangedAtGameTime = startedAtGameTime;
    }

    public void transition(ManifestationState next, long gameTime) {
        ManifestationStateMachine.requireTransition(this.state, next);
        this.state = next;
        this.stateChangedAtGameTime = gameTime;
    }

    public void fail(String reason, long gameTime) {
        if (!this.state.terminal()) {
            transition(ManifestationState.FAILED, gameTime);
        }
        this.failureReason = reason;
    }

    public void updateProgress(double generation, double animation) {
        this.generationProgress = clamp(generation);
        this.animationProgress = clamp(animation);
    }

    public void markItemConsumed(CatalystConsumptionPolicy policy) {
        this.itemConsumed = true;
        this.catalystConsumptionPolicy = policy;
    }

    public void markItemRefunded() {
        this.itemRefunded = true;
    }

    public void forceAnimationComplete() {
        this.animationForced = true;
        this.animationProgress = 1.0;
    }

    public void beginClosing(long gameTime, ManifestationState outcome) {
        if (outcome != ManifestationState.COMPLETE && outcome != ManifestationState.CANCELLED) {
            throw new IllegalArgumentException("Portal closing outcome must be COMPLETE or CANCELLED");
        }
        this.closingOutcome = outcome;
        transition(ManifestationState.CLOSING, gameTime);
    }

    public void cancelAfterClosing() {
        if (this.state != ManifestationState.CLOSING) {
            throw new IllegalStateException("Only a closing portal can change its outcome");
        }
        this.closingOutcome = ManifestationState.CANCELLED;
    }

    public void setPortalColors(ResolvedPortalColors portalColors) {
        this.portalColors = portalColors;
    }

    public UUID id() { return id; }
    public ResourceLocation originDimension() { return originDimension; }
    public BlockPos origin() { return origin; }
    public int rotationDegrees() { return rotationDegrees; }
    public Direction orientation() { return PortalRotation.nearestDirection(rotationDegrees); }
    public InstanceId instanceId() { return instanceId; }
    public ResourceLocation dungeonId() { return dungeonId; }
    public DungeonTarget target() { return target; }
    public InstanceLifecycleOverrides lifecycleOverrides() { return lifecycleOverrides; }
    public long animationSeed() { return animationSeed; }
    public AnimationMode animationMode() { return animationMode; }
    public Optional<UUID> initiator() { return Optional.ofNullable(initiator); }
    public long createdAtMillis() { return createdAtMillis; }
    public long startedAtGameTime() { return startedAtGameTime; }
    public int animationDurationTicks() { return animationDurationTicks; }
    public ManifestationState state() { return state; }
    public long stateChangedAtGameTime() { return stateChangedAtGameTime; }
    public double generationProgress() { return generationProgress; }
    public double animationProgress() { return animationProgress; }
    public Optional<String> failureReason() { return Optional.ofNullable(failureReason); }
    public boolean itemConsumed() { return itemConsumed; }
    public CatalystConsumptionPolicy catalystConsumptionPolicy() { return catalystConsumptionPolicy; }
    public boolean itemRefunded() { return itemRefunded; }
    public boolean animationForced() { return animationForced; }
    public ManifestationState closingOutcome() { return closingOutcome; }
    public ResolvedPortalColors portalColors() { return portalColors; }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("OriginDimension", originDimension.toString());
        tag.putLong("Origin", origin.asLong());
        tag.putInt("RotationDegrees", rotationDegrees);
        tag.putString("Orientation", orientation().getName());
        tag.putUUID("InstanceId", instanceId.value());
        tag.putString("DungeonId", dungeonId.toString());
        tag.putString("TargetKind", target.kind().name());
        target.id().ifPresent(value -> tag.putString("TargetId", value.toString()));
        lifecycleOverrides.openSeconds().ifPresent(value -> tag.putInt("OpenSecondsOverride", value));
        lifecycleOverrides.postVisitSeconds().ifPresent(value -> tag.putInt("PostVisitSecondsOverride", value));
        lifecycleOverrides.forceCollapseSeconds().ifPresent(value -> tag.putInt("ForceCollapseSecondsOverride", value));
        tag.putLong("AnimationSeed", animationSeed);
        tag.putString("AnimationMode", animationMode.name());
        if (initiator != null) tag.putUUID("Initiator", initiator);
        tag.putLong("CreatedAt", createdAtMillis);
        tag.putLong("StartedAtGameTime", startedAtGameTime);
        tag.putInt("AnimationDuration", animationDurationTicks);
        tag.putString("State", state.name());
        tag.putLong("StateChangedAt", stateChangedAtGameTime);
        tag.putDouble("GenerationProgress", generationProgress);
        tag.putDouble("AnimationProgress", animationProgress);
        if (failureReason != null) tag.putString("Failure", failureReason);
        tag.putBoolean("ItemConsumed", itemConsumed);
        tag.putString("CatalystConsumptionPolicy", catalystConsumptionPolicy.name());
        tag.putBoolean("ItemRefunded", itemRefunded);
        tag.putBoolean("AnimationForced", animationForced);
        tag.putString("ClosingOutcome", closingOutcome.name());
        tag.putInt("PortalInnerColor", portalColors.innerColor());
        tag.putInt("PortalOuterColor", portalColors.outerColor());
        return tag;
    }

    static Optional<DungeonManifestation> load(CompoundTag tag) {
        try {
            ResourceLocation dimension = ResourceLocation.parse(tag.getString("OriginDimension"));
            ResourceLocation dungeon = ResourceLocation.parse(tag.getString("DungeonId"));
            DungeonTarget target = tag.contains("TargetKind", Tag.TAG_STRING)
                ? new DungeonTarget(
                    DungeonTarget.Kind.valueOf(tag.getString("TargetKind")),
                    tag.contains("TargetId", Tag.TAG_STRING)
                        ? Optional.of(ResourceLocation.parse(tag.getString("TargetId"))) : Optional.empty())
                : DungeonTarget.dungeon(dungeon);
            DungeonManifestation result = new DungeonManifestation(
                tag.getUUID("Id"), dimension, BlockPos.of(tag.getLong("Origin")),
                tag.contains("RotationDegrees", Tag.TAG_INT)
                    ? tag.getInt("RotationDegrees")
                    : PortalRotation.fromDirection(Direction.byName(tag.getString("Orientation")) == null
                        ? Direction.NORTH
                        : Direction.byName(tag.getString("Orientation"))),
                new InstanceId(tag.getUUID("InstanceId")), dungeon, target,
                new InstanceLifecycleOverrides(
                    tag.contains("OpenSecondsOverride", Tag.TAG_INT)
                        ? Optional.of(tag.getInt("OpenSecondsOverride")) : Optional.empty(),
                    tag.contains("PostVisitSecondsOverride", Tag.TAG_INT)
                        ? Optional.of(tag.getInt("PostVisitSecondsOverride")) : Optional.empty(),
                    tag.contains("ForceCollapseSecondsOverride", Tag.TAG_INT)
                        ? Optional.of(tag.getInt("ForceCollapseSecondsOverride")) : Optional.empty()),
                tag.getLong("AnimationSeed"), AnimationMode.valueOf(tag.getString("AnimationMode")),
                tag.hasUUID("Initiator") ? tag.getUUID("Initiator") : null,
                tag.getLong("CreatedAt"), tag.getLong("StartedAtGameTime"), tag.getInt("AnimationDuration"),
                new ResolvedPortalColors(
                    tag.contains("PortalInnerColor", Tag.TAG_INT) ? tag.getInt("PortalInnerColor") : PortalColor.parseRgba("#010104F5"),
                    tag.contains("PortalOuterColor", Tag.TAG_INT) ? tag.getInt("PortalOuterColor") : PortalColor.parseRgba("#2AAAFF73")));
            result.state = ManifestationState.valueOf(tag.getString("State"));
            result.stateChangedAtGameTime = tag.getLong("StateChangedAt");
            result.generationProgress = tag.getDouble("GenerationProgress");
            result.animationProgress = tag.getDouble("AnimationProgress");
            result.failureReason = tag.contains("Failure", Tag.TAG_STRING) ? tag.getString("Failure") : null;
            result.itemConsumed = tag.getBoolean("ItemConsumed");
            result.catalystConsumptionPolicy = tag.contains("CatalystConsumptionPolicy", Tag.TAG_STRING)
                ? CatalystConsumptionPolicy.valueOf(tag.getString("CatalystConsumptionPolicy"))
                : (result.itemConsumed ? CatalystConsumptionPolicy.ON_ACTIVATION : CatalystConsumptionPolicy.NEVER);
            result.itemRefunded = tag.getBoolean("ItemRefunded");
            result.animationForced = tag.getBoolean("AnimationForced");
            if (tag.contains("ClosingOutcome", Tag.TAG_STRING)) {
                ManifestationState outcome = ManifestationState.valueOf(tag.getString("ClosingOutcome"));
                if (outcome == ManifestationState.COMPLETE || outcome == ManifestationState.CANCELLED) {
                    result.closingOutcome = outcome;
                }
            }
            return Optional.of(result);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
