package com.cappleapple.instancednotinfinite.network;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.manifestation.AnimationMode;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationState;
import com.cappleapple.instancednotinfinite.manifestation.PreparationParticleStyle;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ManifestationStartPayload(
    UUID id,
    ResourceLocation dimension,
    BlockPos origin,
    int rotationDegrees,
    UUID instanceId,
    ResourceLocation dungeonId,
    long animationSeed,
    AnimationMode animationMode,
    ManifestationState state,
    float generationProgress,
    float animationProgress,
    long stateChangedGameTime,
    int sizeX,
    int sizeY,
    int sizeZ,
    int visualMinX,
    int visualMinY,
    int visualMinZ,
    int visualMaxX,
    int visualMaxY,
    int visualMaxZ,
    float maximumWidth,
    float maximumHeight,
    float maximumDepth,
    float terrainAlpha,
    float structureAlpha,
    int collapseDurationTicks,
    int portalGrowthDurationTicks,
    int portalCloseDurationTicks,
    float portalWidth,
    float portalHeight,
    float portalDepth,
    float portalMinimumWidth,
    float portalMinimumHeight,
    float portalMinimumDepth,
    int portalInnerColor,
    int portalOuterColor,
    PreparationParticleStyle preparationParticleStyle,
    int preparationParticleColor,
    int preparationParticleRate,
    float preparationParticleScale,
    float preparationParticleRadius
) implements CustomPacketPayload {
    public static final Type<ManifestationStartPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(InstancedNotInfinite.MOD_ID, "manifestation_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManifestationStartPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ManifestationStartPayload::write, ManifestationStartPayload::new);

    private ManifestationStartPayload(RegistryFriendlyByteBuf buffer) {
        this(
            buffer.readUUID(), buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readVarInt(),
            buffer.readUUID(), buffer.readResourceLocation(), buffer.readLong(), buffer.readEnum(AnimationMode.class),
            buffer.readEnum(ManifestationState.class), buffer.readFloat(), buffer.readFloat(), buffer.readLong(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
            buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
            buffer.readInt(), buffer.readInt(), buffer.readEnum(PreparationParticleStyle.class),
            buffer.readInt(), buffer.readVarInt(), buffer.readFloat(), buffer.readFloat());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeResourceLocation(dimension);
        buffer.writeBlockPos(origin);
        buffer.writeVarInt(rotationDegrees);
        buffer.writeUUID(instanceId);
        buffer.writeResourceLocation(dungeonId);
        buffer.writeLong(animationSeed);
        buffer.writeEnum(animationMode);
        buffer.writeEnum(state);
        buffer.writeFloat(generationProgress);
        buffer.writeFloat(animationProgress);
        buffer.writeLong(stateChangedGameTime);
        buffer.writeVarInt(sizeX);
        buffer.writeVarInt(sizeY);
        buffer.writeVarInt(sizeZ);
        buffer.writeVarInt(visualMinX);
        buffer.writeVarInt(visualMinY);
        buffer.writeVarInt(visualMinZ);
        buffer.writeVarInt(visualMaxX);
        buffer.writeVarInt(visualMaxY);
        buffer.writeVarInt(visualMaxZ);
        buffer.writeFloat(maximumWidth);
        buffer.writeFloat(maximumHeight);
        buffer.writeFloat(maximumDepth);
        buffer.writeFloat(terrainAlpha);
        buffer.writeFloat(structureAlpha);
        buffer.writeVarInt(collapseDurationTicks);
        buffer.writeVarInt(portalGrowthDurationTicks);
        buffer.writeVarInt(portalCloseDurationTicks);
        buffer.writeFloat(portalWidth);
        buffer.writeFloat(portalHeight);
        buffer.writeFloat(portalDepth);
        buffer.writeFloat(portalMinimumWidth);
        buffer.writeFloat(portalMinimumHeight);
        buffer.writeFloat(portalMinimumDepth);
        buffer.writeInt(portalInnerColor);
        buffer.writeInt(portalOuterColor);
        buffer.writeEnum(preparationParticleStyle);
        buffer.writeInt(preparationParticleColor);
        buffer.writeVarInt(preparationParticleRate);
        buffer.writeFloat(preparationParticleScale);
        buffer.writeFloat(preparationParticleRadius);
    }

    @Override public Type<ManifestationStartPayload> type() { return TYPE; }
}
