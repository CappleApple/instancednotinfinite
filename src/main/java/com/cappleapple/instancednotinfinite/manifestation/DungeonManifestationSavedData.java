package com.cappleapple.instancednotinfinite.manifestation;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class DungeonManifestationSavedData extends SavedData {
    private static final String DATA_NAME = InstancedNotInfinite.MOD_ID + "_manifestations";
    private static final SavedData.Factory<DungeonManifestationSavedData> FACTORY = new SavedData.Factory<>(
        DungeonManifestationSavedData::new, DungeonManifestationSavedData::load);
    private final Map<UUID, DungeonManifestation> values = new LinkedHashMap<>();

    public static DungeonManifestationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Collection<DungeonManifestation> values() { return List.copyOf(values.values()); }
    public Optional<DungeonManifestation> get(UUID id) { return Optional.ofNullable(values.get(id)); }
    public void put(DungeonManifestation value) { values.put(value.id(), value); setDirty(); }
    public void changed() { setDirty(); }
    public void remove(UUID id) { if (values.remove(id) != null) setDirty(); }

    private DungeonManifestationSavedData() {
    }

    private static DungeonManifestationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonManifestationSavedData data = new DungeonManifestationSavedData();
        ListTag list = tag.getList("Manifestations", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            DungeonManifestation.load(list.getCompound(index)).ifPresent(value -> data.values.put(value.id(), value));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        values.values().forEach(value -> list.add(value.save()));
        tag.put("Manifestations", list);
        return tag;
    }
}
