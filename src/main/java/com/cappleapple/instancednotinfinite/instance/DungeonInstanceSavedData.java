package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class DungeonInstanceSavedData extends SavedData {
    private static final String DATA_NAME = InstancedNotInfinite.MOD_ID + "_instances";
    private static final SavedData.Factory<DungeonInstanceSavedData> FACTORY = new SavedData.Factory<>(
        DungeonInstanceSavedData::new,
        DungeonInstanceSavedData::load);

    private final Map<InstanceId, DungeonInstance> instances = new LinkedHashMap<>();

    public static DungeonInstanceSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Collection<DungeonInstance> values() {
        return List.copyOf(this.instances.values());
    }

    public Optional<DungeonInstance> get(InstanceId id) {
        return Optional.ofNullable(this.instances.get(id));
    }

    public void put(DungeonInstance instance) {
        this.instances.put(instance.id(), instance);
        this.setDirty();
    }

    public void changed() {
        this.setDirty();
    }

    public void remove(InstanceId id) {
        if (this.instances.remove(id) != null) {
            this.setDirty();
        }
    }

    private DungeonInstanceSavedData() {
    }

    private static DungeonInstanceSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonInstanceSavedData data = new DungeonInstanceSavedData();
        ListTag list = tag.getList("Instances", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            DungeonInstance.load(list.getCompound(index)).ifPresent(instance -> data.instances.put(instance.id(), instance));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        this.instances.values().forEach(instance -> list.add(instance.save()));
        tag.put("Instances", list);
        return tag;
    }
}
