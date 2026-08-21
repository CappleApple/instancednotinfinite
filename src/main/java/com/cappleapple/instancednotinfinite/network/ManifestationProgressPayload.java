package com.cappleapple.instancednotinfinite.network;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationState;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ManifestationProgressPayload(
    UUID id,
    ManifestationState state,
    float generationProgress,
    float animationProgress,
    long stateChangedGameTime,
    int portalInnerColor,
    int portalOuterColor,
    int portalCountdownTotalTicks,
    int portalCountdownRemainingTicks,
    boolean portalCountdownActive
) implements CustomPacketPayload {
    public static final Type<ManifestationProgressPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(InstancedNotInfinite.MOD_ID, "manifestation_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManifestationProgressPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ManifestationProgressPayload::write, ManifestationProgressPayload::new);

    private ManifestationProgressPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readEnum(ManifestationState.class), buffer.readFloat(), buffer.readFloat(), buffer.readLong(),
            buffer.readInt(), buffer.readInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeEnum(state);
        buffer.writeFloat(generationProgress);
        buffer.writeFloat(animationProgress);
        buffer.writeLong(stateChangedGameTime);
        buffer.writeInt(portalInnerColor);
        buffer.writeInt(portalOuterColor);
        buffer.writeVarInt(portalCountdownTotalTicks);
        buffer.writeVarInt(portalCountdownRemainingTicks);
        buffer.writeBoolean(portalCountdownActive);
    }

    @Override public Type<ManifestationProgressPayload> type() { return TYPE; }
}
