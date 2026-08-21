package com.cappleapple.instancednotinfinite.network;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonCatalogPayload(
    UUID worldId,
    List<DungeonEntry> dungeons,
    List<StructurePoolEntry> structurePools
) implements CustomPacketPayload {
    public static final Type<DungeonCatalogPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(InstancedNotInfinite.MOD_ID, "dungeon_catalog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonCatalogPayload> STREAM_CODEC =
        CustomPacketPayload.codec(DungeonCatalogPayload::write, DungeonCatalogPayload::new);

    public DungeonCatalogPayload {
        dungeons = dungeons.stream().distinct()
            .sorted(java.util.Comparator.comparing(DungeonEntry::dungeonId)).toList();
        structurePools = structurePools.stream().distinct()
            .sorted(java.util.Comparator.comparing(StructurePoolEntry::tagId)).toList();
    }

    private DungeonCatalogPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readList(DungeonEntry::new),
            buffer.readList(StructurePoolEntry::new));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(worldId);
        buffer.writeCollection(dungeons, (output, entry) -> entry.write(output));
        buffer.writeCollection(structurePools, (output, entry) -> entry.write(output));
    }

    @Override
    public Type<DungeonCatalogPayload> type() {
        return TYPE;
    }

    public record DungeonEntry(
        ResourceLocation dungeonId,
        int portalInnerColor,
        int portalOuterColor,
        boolean exactCatalyst
    ) {
        private DungeonEntry(FriendlyByteBuf buffer) {
            this(buffer.readResourceLocation(), buffer.readInt(), buffer.readInt(), buffer.readBoolean());
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(dungeonId);
            buffer.writeInt(portalInnerColor);
            buffer.writeInt(portalOuterColor);
            buffer.writeBoolean(exactCatalyst);
        }
    }

    public record StructurePoolEntry(ResourceLocation tagId, List<ResourceLocation> dungeonIds) {
        public StructurePoolEntry {
            dungeonIds = dungeonIds.stream().distinct().sorted().toList();
        }

        private StructurePoolEntry(FriendlyByteBuf buffer) {
            this(buffer.readResourceLocation(), buffer.readList(input -> input.readResourceLocation()));
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(tagId);
            buffer.writeCollection(dungeonIds, (output, id) -> output.writeResourceLocation(id));
        }
    }
}
