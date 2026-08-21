package com.cappleapple.instancednotinfinite.network;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Stable, non-secret world identity used to scope client-side generated miniature caches. */
public final class ClientCacheIdentitySavedData extends SavedData {
    private static final String DATA_NAME = InstancedNotInfinite.MOD_ID + "_client_cache_identity";
    private static final SavedData.Factory<ClientCacheIdentitySavedData> FACTORY = new SavedData.Factory<>(
        ClientCacheIdentitySavedData::create,
        ClientCacheIdentitySavedData::load);

    private final UUID worldId;

    public static UUID get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME).worldId;
    }

    private ClientCacheIdentitySavedData(UUID worldId) {
        this.worldId = worldId;
    }

    private static ClientCacheIdentitySavedData create() {
        ClientCacheIdentitySavedData data = new ClientCacheIdentitySavedData(UUID.randomUUID());
        data.setDirty();
        return data;
    }

    private static ClientCacheIdentitySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return tag.hasUUID("WorldId")
            ? new ClientCacheIdentitySavedData(tag.getUUID("WorldId"))
            : create();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putUUID("WorldId", this.worldId);
        return tag;
    }
}
