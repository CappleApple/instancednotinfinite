package com.cappleapple.instancednotinfinite.player;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class PlayerReturnSavedData extends SavedData {
    private static final String DATA_NAME = InstancedNotInfinite.MOD_ID + "_player_returns";
    private static final SavedData.Factory<PlayerReturnSavedData> FACTORY = new SavedData.Factory<>(
        PlayerReturnSavedData::new,
        PlayerReturnSavedData::load);

    private final Map<UUID, ReturnLocation> returns = new LinkedHashMap<>();

    public static PlayerReturnSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Optional<ReturnLocation> get(UUID playerId) {
        return Optional.ofNullable(this.returns.get(playerId));
    }

    public void putIfAbsent(UUID playerId, ReturnLocation location) {
        if (this.returns.putIfAbsent(playerId, location) == null) {
            this.setDirty();
        }
    }

    public void remove(UUID playerId) {
        if (this.returns.remove(playerId) != null) {
            this.setDirty();
        }
    }

    private PlayerReturnSavedData() {
    }

    private static PlayerReturnSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerReturnSavedData data = new PlayerReturnSavedData();
        ListTag list = tag.getList("Returns", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            try {
                data.returns.put(entry.getUUID("Player"), ReturnLocation.load(entry.getCompound("Location")));
            } catch (RuntimeException exception) {
                InstancedNotInfinite.LOGGER.error("Ignoring invalid persisted dungeon return location at index {}", index, exception);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        this.returns.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag saved = new CompoundTag();
            saved.putUUID("Player", entry.getKey());
            saved.put("Location", entry.getValue().save());
            list.add(saved);
        });
        tag.put("Returns", list);
        return tag;
    }
}
