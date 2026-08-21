package com.cappleapple.instancednotinfinite.network;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.snapshot.VisualBlock;
import com.cappleapple.instancednotinfinite.snapshot.VisualLayer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public record ManifestationBlocksPayload(UUID id, List<Entry> blocks) implements CustomPacketPayload {
    public static final Type<ManifestationBlocksPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(InstancedNotInfinite.MOD_ID, "manifestation_blocks"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManifestationBlocksPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ManifestationBlocksPayload::write, ManifestationBlocksPayload::new);

    public ManifestationBlocksPayload(UUID id, List<VisualBlock> blocks, boolean encode) {
        this(id, blocks.stream().map(block -> new Entry(
            block.position().getX(), block.position().getY(), block.position().getZ(),
            Block.BLOCK_STATE_REGISTRY.getId(block.state()), block.layer())).toList());
    }

    private ManifestationBlocksPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), readEntries(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeVarInt(blocks.size());
        blocks.forEach(entry -> entry.write(buffer));
    }

    private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 4096) throw new IllegalArgumentException("Invalid manifestation block batch size " + size);
        List<Entry> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) result.add(new Entry(buffer));
        return List.copyOf(result);
    }

    @Override public Type<ManifestationBlocksPayload> type() { return TYPE; }

    public record Entry(int x, int y, int z, int blockStateId, VisualLayer layer) {
        private Entry(RegistryFriendlyByteBuf buffer) {
            this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readEnum(VisualLayer.class));
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(x);
            buffer.writeVarInt(y);
            buffer.writeVarInt(z);
            buffer.writeVarInt(blockStateId);
            buffer.writeEnum(layer);
        }

        public BlockPos position() { return new BlockPos(x, y, z); }
    }
}
