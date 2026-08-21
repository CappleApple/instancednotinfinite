package com.cappleapple.instancednotinfinite.network;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ManifestationRemovePayload(UUID id) implements CustomPacketPayload {
    public static final Type<ManifestationRemovePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(InstancedNotInfinite.MOD_ID, "manifestation_remove"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManifestationRemovePayload> STREAM_CODEC =
        CustomPacketPayload.codec(ManifestationRemovePayload::write, ManifestationRemovePayload::new);

    private ManifestationRemovePayload(RegistryFriendlyByteBuf buffer) { this(buffer.readUUID()); }
    private void write(RegistryFriendlyByteBuf buffer) { buffer.writeUUID(id); }
    @Override public Type<ManifestationRemovePayload> type() { return TYPE; }
}
