package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.definition.DungeonDefinition;
import com.cappleapple.instancednotinfinite.definition.StructureKind;
import com.cappleapple.instancednotinfinite.manifestation.ResolvedPortalColors;
import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonInstance {
    private final InstanceId id;
    private DungeonDefinition definition;
    private final ResourceLocation dimensionId;
    private final ResourceLocation structureId;
    private final StructureKind structureKind;
    private final ResourceLocation biomeId;
    private final long seed;
    private final long createdAtMillis;
    private final InstanceLifecycleSettings lifecycleSettings;
    private final Set<UUID> assignedPlayers;
    private InstanceState state;
    private long stateChangedAtMillis;
    private long vacantSinceMillis;
    private long completedAtMillis;
    private long openedAtMillis;
    private long lastCleanupAttemptMillis;
    private String failureReason;
    private GenerationPlan plan;
    private ResolvedPortalColors portalColors;

    public DungeonInstance(
        InstanceId id,
        DungeonDefinition definition,
        ResourceLocation dimensionId,
        ResourceLocation structureId,
        StructureKind structureKind,
        ResourceLocation biomeId,
        long seed,
        long createdAtMillis,
        InstanceLifecycleSettings lifecycleSettings
    ) {
        this.id = id;
        this.definition = definition;
        this.dimensionId = dimensionId;
        this.structureId = structureId;
        this.structureKind = structureKind;
        this.biomeId = biomeId;
        this.seed = seed;
        this.createdAtMillis = createdAtMillis;
        this.lifecycleSettings = lifecycleSettings;
        this.state = InstanceState.CREATING;
        this.stateChangedAtMillis = createdAtMillis;
        this.assignedPlayers = new LinkedHashSet<>();
    }

    public DungeonInstance(
        InstanceId id,
        DungeonDefinition definition,
        ResourceLocation dimensionId,
        ResourceLocation structureId,
        StructureKind structureKind,
        ResourceLocation biomeId,
        long seed,
        long createdAtMillis
    ) {
        this(id, definition, dimensionId, structureId, structureKind, biomeId, seed, createdAtMillis,
            InstanceLifecycleSettings.DEFAULT);
    }

    public void transition(InstanceState next, long nowMillis) {
        InstanceStateMachine.requireTransition(this.state, next);
        this.state = next;
        this.stateChangedAtMillis = nowMillis;
        if (next == InstanceState.VACANT) {
            this.vacantSinceMillis = nowMillis;
        }
        if (next == InstanceState.ACTIVE && this.openedAtMillis == 0L) {
            this.openedAtMillis = nowMillis;
        }
        if (next == InstanceState.COMPLETED) {
            this.completedAtMillis = nowMillis;
        }
    }

    public void fail(String reason, long nowMillis) {
        if (this.state != InstanceState.FAILED) {
            InstanceStateMachine.requireTransition(this.state, InstanceState.FAILED);
            this.state = InstanceState.FAILED;
            this.stateChangedAtMillis = nowMillis;
        }
        this.failureReason = reason;
    }

    public void setPlan(GenerationPlan plan) {
        this.definition = plan.definition();
        this.plan = plan;
    }

    public void setPortalColors(ResolvedPortalColors portalColors) {
        this.portalColors = portalColors;
    }

    public void assign(UUID playerId) {
        this.assignedPlayers.add(playerId);
    }

    public void unassign(UUID playerId) {
        this.assignedPlayers.remove(playerId);
    }

    public void markCleanupAttempt(long nowMillis) {
        this.lastCleanupAttemptMillis = nowMillis;
    }

    public InstanceId id() {
        return id;
    }

    public DungeonDefinition definition() {
        return definition;
    }

    public ResourceLocation dimensionId() {
        return dimensionId;
    }

    public ResourceLocation structureId() {
        return structureId;
    }

    public StructureKind structureKind() {
        return structureKind;
    }

    public ResourceLocation biomeId() {
        return biomeId;
    }

    public long seed() {
        return seed;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public InstanceLifecycleSettings lifecycleSettings() {
        return lifecycleSettings;
    }

    public long openedAtMillis() {
        return openedAtMillis == 0L ? createdAtMillis : openedAtMillis;
    }

    public InstanceState state() {
        return state;
    }

    public long stateChangedAtMillis() {
        return stateChangedAtMillis;
    }

    public long vacantSinceMillis() {
        return vacantSinceMillis;
    }

    public long completedAtMillis() {
        return completedAtMillis;
    }

    public long lastCleanupAttemptMillis() {
        return lastCleanupAttemptMillis;
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
    }

    public Optional<GenerationPlan> plan() {
        return Optional.ofNullable(plan);
    }

    public Optional<ResolvedPortalColors> portalColors() {
        return Optional.ofNullable(portalColors);
    }

    public Set<UUID> assignedPlayers() {
        return Set.copyOf(assignedPlayers);
    }

    public boolean everEntered() {
        return !this.assignedPlayers.isEmpty();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id.value());
        tag.put("Definition", DungeonDefinitionNbt.save(this.definition));
        tag.putString("Dimension", this.dimensionId.toString());
        tag.putString("Structure", this.structureId.toString());
        tag.putString("StructureKind", this.structureKind.name());
        tag.putString("Biome", this.biomeId.toString());
        tag.putLong("Seed", this.seed);
        tag.putLong("CreatedAt", this.createdAtMillis);
        tag.putString("State", this.state.name());
        tag.putLong("StateChangedAt", this.stateChangedAtMillis);
        tag.putLong("VacantSince", this.vacantSinceMillis);
        tag.putLong("CompletedAt", this.completedAtMillis);
        tag.putLong("OpenedAt", this.openedAtMillis);
        CompoundTag lifecycle = new CompoundTag();
        lifecycle.putInt("OpenSeconds", this.lifecycleSettings.openSeconds());
        lifecycle.putInt("PostVisitSeconds", this.lifecycleSettings.postVisitSeconds());
        lifecycle.putInt("ForceCollapseSeconds", this.lifecycleSettings.forceCollapseSeconds());
        tag.put("Lifecycle", lifecycle);
        tag.putLong("LastCleanupAttempt", this.lastCleanupAttemptMillis);
        if (this.failureReason != null) {
            tag.putString("Failure", this.failureReason);
        }
        ListTag players = new ListTag();
        this.assignedPlayers.stream().sorted().forEach(uuid -> players.add(NbtUtils.createUUID(uuid)));
        tag.put("Players", players);
        if (this.plan != null) {
            tag.put("Plan", savePlan(this.plan));
        }
        if (this.portalColors != null) {
            tag.putInt("PortalInnerColor", this.portalColors.innerColor());
            tag.putInt("PortalOuterColor", this.portalColors.outerColor());
        }
        return tag;
    }

    static Optional<DungeonInstance> load(CompoundTag tag) {
        try {
            InstanceId id = new InstanceId(tag.getUUID("Id"));
            DungeonDefinition definition = DungeonDefinitionNbt.load(tag.getCompound("Definition"));
            ResourceLocation dimension = requireId(tag.getString("Dimension"));
            ResourceLocation structure = requireId(tag.getString("Structure"));
            ResourceLocation biome = requireId(tag.getString("Biome"));
            InstanceLifecycleSettings lifecycle = tag.contains("Lifecycle", Tag.TAG_COMPOUND)
                ? loadLifecycle(tag.getCompound("Lifecycle"))
                : InstanceLifecycleSettings.DEFAULT;
            DungeonInstance instance = new DungeonInstance(
                id, definition, dimension, structure,
                StructureKind.valueOf(tag.getString("StructureKind")), biome,
                tag.getLong("Seed"), tag.getLong("CreatedAt"), lifecycle);
            instance.state = InstanceState.valueOf(tag.getString("State"));
            instance.stateChangedAtMillis = tag.getLong("StateChangedAt");
            instance.vacantSinceMillis = tag.getLong("VacantSince");
            instance.completedAtMillis = tag.getLong("CompletedAt");
            instance.openedAtMillis = tag.contains("OpenedAt", Tag.TAG_LONG)
                ? tag.getLong("OpenedAt")
                : (instance.state == InstanceState.CREATING ? 0L : instance.stateChangedAtMillis);
            instance.lastCleanupAttemptMillis = tag.getLong("LastCleanupAttempt");
            instance.failureReason = tag.contains("Failure", Tag.TAG_STRING) ? tag.getString("Failure") : null;
            ListTag players = tag.getList("Players", Tag.TAG_INT_ARRAY);
            for (int index = 0; index < players.size(); index++) {
                instance.assignedPlayers.add(NbtUuid.uuidFromIntArray(players.getIntArray(index)));
            }
            if (tag.contains("Plan", Tag.TAG_COMPOUND)) {
                instance.plan = loadPlan(tag.getCompound("Plan"), instance.seed, instance.definition);
            }
            if (tag.contains("PortalInnerColor", Tag.TAG_INT) && tag.contains("PortalOuterColor", Tag.TAG_INT)) {
                instance.portalColors = new ResolvedPortalColors(
                    tag.getInt("PortalInnerColor"), tag.getInt("PortalOuterColor"));
            }
            return Optional.of(instance);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static InstanceLifecycleSettings loadLifecycle(CompoundTag tag) {
        return new InstanceLifecycleSettings(
            tag.contains("OpenSeconds", Tag.TAG_INT) ? tag.getInt("OpenSeconds") : InstanceLifecycleSettings.DEFAULT.openSeconds(),
            tag.contains("PostVisitSeconds", Tag.TAG_INT) ? tag.getInt("PostVisitSeconds") : InstanceLifecycleSettings.DEFAULT.postVisitSeconds(),
            tag.contains("ForceCollapseSeconds", Tag.TAG_INT)
                ? tag.getInt("ForceCollapseSeconds") : InstanceLifecycleSettings.DEFAULT.forceCollapseSeconds());
    }

    private static CompoundTag savePlan(GenerationPlan plan) {
        CompoundTag tag = new CompoundTag();
        putBox(tag, "Structure", plan.structureBounds());
        putBox(tag, "Guaranteed", plan.guaranteedBounds());
        putBox(tag, "Envelope", plan.envelopeBounds());
        tag.putLong("Origin", plan.structureOrigin().asLong());
        tag.putInt("TerrainSurfaceY", plan.terrainSurfaceY());
        if (plan.oceanFloorY() != null) tag.putInt("OceanFloorY", plan.oceanFloorY());
        tag.putBoolean("FloatingVoid", plan.floatingVoid());
        tag.putLong("Entry", plan.entryPosition().asLong());
        tag.putFloat("EntryYaw", plan.entryYaw());
        return tag;
    }

    private static GenerationPlan loadPlan(CompoundTag tag, long seed, DungeonDefinition definition) {
        BoundingBox structure = getBox(tag, "Structure");
        BoundingBox envelope = getBox(tag, "Envelope");
        BoundingBox guaranteed = tag.contains("Guaranteed", Tag.TAG_INT_ARRAY)
            ? getBox(tag, "Guaranteed")
            : envelope;
        return new GenerationPlan(
            seed, definition, structure, guaranteed, envelope,
            BlockPos.of(tag.getLong("Origin")),
            tag.contains("TerrainSurfaceY", Tag.TAG_INT) ? tag.getInt("TerrainSurfaceY") : structure.minY() - 1,
            BlockPos.of(tag.getLong("Entry")),
            tag.contains("EntryYaw", Tag.TAG_FLOAT) ? tag.getFloat("EntryYaw") : definition.entry().yaw(),
            tag.contains("OceanFloorY", Tag.TAG_INT) ? tag.getInt("OceanFloorY") : null,
            tag.getBoolean("FloatingVoid"));
    }

    private static void putBox(CompoundTag tag, String key, BoundingBox box) {
        tag.putIntArray(key, new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
    }

    private static BoundingBox getBox(CompoundTag tag, String key) {
        int[] values = tag.getIntArray(key);
        if (values.length != 6) {
            throw new IllegalArgumentException("Invalid persisted bounding box " + key);
        }
        return new BoundingBox(values[0], values[1], values[2], values[3], values[4], values[5]);
    }

    private static ResourceLocation requireId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid persisted resource location " + value);
        }
        return id;
    }
}
