package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.network.ManifestationBlocksPayload;
import com.cappleapple.instancednotinfinite.network.ManifestationProgressPayload;
import com.cappleapple.instancednotinfinite.network.ManifestationStartPayload;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public final class ClientManifestationStore {
    private static final Map<UUID, ClientManifestation> VALUES = new LinkedHashMap<>();

    private ClientManifestationStore() {
    }

    static void start(ManifestationStartPayload payload) {
        VALUES.compute(payload.id(), (id, existing) -> {
            if (existing == null) return new ClientManifestation(payload);
            existing.update(payload);
            return existing;
        });
    }

    static void add(ManifestationBlocksPayload payload) {
        ClientManifestation value = VALUES.get(payload.id());
        if (value != null) {
            value.add(payload);
            HologramMeshCache.markDirty(value);
            DungeonIconCache.invalidate(value);
        }
    }

    static void progress(ManifestationProgressPayload payload) {
        ClientManifestation value = VALUES.get(payload.id());
        if (value != null) {
            value.update(payload);
            DungeonIconCache.prime(value);
        }
    }

    static void remove(UUID id) {
        HologramMeshCache.remove(id);
        VALUES.remove(id);
    }

    public static Collection<ClientManifestation> values() { return List.copyOf(VALUES.values()); }

    static boolean contains(UUID id) { return VALUES.containsKey(id); }

    public static Optional<ClientManifestation> find(UUID id) {
        return Optional.ofNullable(VALUES.get(id));
    }

    public static Optional<ClientManifestation> findByDungeon(ResourceLocation dungeonId) {
        return VALUES.values().stream()
            .filter(value -> value.dungeonId().equals(dungeonId))
            .filter(value -> value.blockCount() > 0)
            .max(java.util.Comparator.comparingInt(ClientManifestation::blockCount));
    }

    static void tick() {
        VALUES.values().forEach(value -> {
            value.tick();
            ManifestationPreparationParticles.tick(value);
        });
        HologramMeshCache.tick();
        ClientDungeonCatalog.tick();
        DungeonIconCache.tick();
    }

    public static void clear() {
        HologramMeshCache.clear();
        VALUES.clear();
        DungeonIconCache.clear();
    }

    static void resourcesReloaded() {
        HologramMeshCache.clear();
        DungeonIconCache.resourcesReloaded();
    }
}
