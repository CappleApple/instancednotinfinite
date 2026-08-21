package com.cappleapple.instancednotinfinite.content;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class ManifestationPortalBlockEntity extends BlockEntity {
    private static final Set<ManifestationPortalBlockEntity> LOADED = ConcurrentHashMap.newKeySet();
    private UUID manifestationId;
    private UUID instanceId;
    private ResourceLocation dungeonId;
    private Endpoint endpoint = Endpoint.ENTRY;
    private int rotationDegrees = 180;
    private float portalWidth = 1.5F;
    private float portalHeight = 2.5F;
    private float portalDepth = 0.35F;
    private float portalMinimumWidth = 1.0F;
    private float portalMinimumHeight = 2.0F;
    private float portalMinimumDepth = 0.35F;
    private int portalInnerColor = 0xF5010104;
    private int portalOuterColor = 0x732AAAFF;

    public ManifestationPortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MANIFESTATION_PORTAL_BLOCK_ENTITY.get(), pos, state);
    }

    public void bind(
        UUID id,
        int rotationDegrees,
        float width,
        float height,
        float depth,
        float minimumWidth,
        float minimumHeight,
        float minimumDepth,
        int innerColor,
        int outerColor
    ) {
        this.endpoint = Endpoint.ENTRY;
        this.manifestationId = id;
        this.instanceId = null;
        this.dungeonId = null;
        this.rotationDegrees = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.normalize(rotationDegrees);
        this.portalWidth = width;
        this.portalHeight = height;
        this.portalDepth = depth;
        this.portalMinimumWidth = Math.min(width, minimumWidth);
        this.portalMinimumHeight = Math.min(height, minimumHeight);
        this.portalMinimumDepth = Math.min(depth, minimumDepth);
        this.portalInnerColor = innerColor;
        this.portalOuterColor = outerColor;
        sync();
    }

    public void bindReturn(
        UUID id,
        ResourceLocation dungeonId,
        int rotationDegrees,
        float width,
        float height,
        float depth,
        float minimumWidth,
        float minimumHeight,
        float minimumDepth,
        int innerColor,
        int outerColor
    ) {
        this.endpoint = Endpoint.RETURN;
        this.manifestationId = null;
        this.instanceId = id;
        this.dungeonId = dungeonId;
        this.rotationDegrees = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.normalize(rotationDegrees);
        this.portalWidth = width;
        this.portalHeight = height;
        this.portalDepth = depth;
        this.portalMinimumWidth = Math.min(width, minimumWidth);
        this.portalMinimumHeight = Math.min(height, minimumHeight);
        this.portalMinimumDepth = Math.min(depth, minimumDepth);
        this.portalInnerColor = innerColor;
        this.portalOuterColor = outerColor;
        sync();
    }

    public Optional<UUID> manifestationId() { return Optional.ofNullable(manifestationId); }
    public Optional<UUID> instanceId() { return Optional.ofNullable(instanceId); }
    public Optional<ResourceLocation> dungeonId() { return Optional.ofNullable(dungeonId); }
    public Endpoint endpoint() { return endpoint; }
    public int rotationDegrees() { return rotationDegrees; }
    public Direction orientation() {
        return com.cappleapple.instancednotinfinite.manifestation.PortalRotation.nearestDirection(rotationDegrees);
    }
    public float portalWidth() { return portalWidth; }
    public float portalHeight() { return portalHeight; }
    public float portalDepth() { return portalDepth; }
    public float portalMinimumWidth() { return portalMinimumWidth; }
    public float portalMinimumHeight() { return portalMinimumHeight; }
    public float portalMinimumDepth() { return portalMinimumDepth; }
    public int portalInnerColor() { return portalInnerColor; }
    public int portalOuterColor() { return portalOuterColor; }
    public AABB interactionBounds() {
        return PortalInteractionShape.bounds(worldPosition, rotationDegrees, portalWidth, portalHeight, portalDepth);
    }
    public boolean intersects(AABB bounds) {
        return PortalInteractionShape.intersects(
            worldPosition, rotationDegrees, portalWidth, portalHeight, portalDepth, bounds);
    }

    /** Loaded endpoint anchors, used client-side to target the complete rendered portal instead of only its block. */
    public static List<ManifestationPortalBlockEntity> loadedIn(Level level) {
        LOADED.removeIf(portal -> portal.isRemoved() || portal.getLevel() == null);
        return LOADED.stream().filter(portal -> portal.getLevel() == level).toList();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        LOADED.remove(this);
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level != null) LOADED.add(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        manifestationId = tag.hasUUID("Manifestation") ? tag.getUUID("Manifestation") : null;
        instanceId = tag.hasUUID("Instance") ? tag.getUUID("Instance") : null;
        dungeonId = ResourceLocation.tryParse(tag.getString("DungeonId"));
        try {
            endpoint = Endpoint.valueOf(tag.getString("Endpoint"));
        } catch (IllegalArgumentException exception) {
            endpoint = instanceId == null ? Endpoint.ENTRY : Endpoint.RETURN;
        }
        if (tag.contains("RotationDegrees")) {
            rotationDegrees = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.normalize(
                tag.getInt("RotationDegrees"));
        } else {
            Direction loaded = Direction.byName(tag.getString("Orientation"));
            rotationDegrees = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.fromDirection(
                loaded != null && loaded.getAxis().isHorizontal() ? loaded : Direction.NORTH);
        }
        if (tag.contains("PortalWidth")) portalWidth = tag.getFloat("PortalWidth");
        if (tag.contains("PortalHeight")) portalHeight = tag.getFloat("PortalHeight");
        if (tag.contains("PortalDepth")) portalDepth = tag.getFloat("PortalDepth");
        if (tag.contains("PortalMinimumWidth")) portalMinimumWidth = tag.getFloat("PortalMinimumWidth");
        if (tag.contains("PortalMinimumHeight")) portalMinimumHeight = tag.getFloat("PortalMinimumHeight");
        if (tag.contains("PortalMinimumDepth")) portalMinimumDepth = tag.getFloat("PortalMinimumDepth");
        if (tag.contains("PortalInnerColor")) portalInnerColor = tag.getInt("PortalInnerColor");
        if (tag.contains("PortalOuterColor")) portalOuterColor = tag.getInt("PortalOuterColor");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (manifestationId != null) tag.putUUID("Manifestation", manifestationId);
        if (instanceId != null) tag.putUUID("Instance", instanceId);
        if (dungeonId != null) tag.putString("DungeonId", dungeonId.toString());
        tag.putString("Endpoint", endpoint.name());
        tag.putInt("RotationDegrees", rotationDegrees);
        tag.putString("Orientation", orientation().getName());
        tag.putFloat("PortalWidth", portalWidth);
        tag.putFloat("PortalHeight", portalHeight);
        tag.putFloat("PortalDepth", portalDepth);
        tag.putFloat("PortalMinimumWidth", portalMinimumWidth);
        tag.putFloat("PortalMinimumHeight", portalMinimumHeight);
        tag.putFloat("PortalMinimumDepth", portalMinimumDepth);
        tag.putInt("PortalInnerColor", portalInnerColor);
        tag.putInt("PortalOuterColor", portalOuterColor);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public enum Endpoint {
        ENTRY,
        RETURN
    }
}
