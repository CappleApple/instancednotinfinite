package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.network.ManifestationBlocksPayload;
import com.cappleapple.instancednotinfinite.network.ManifestationProgressPayload;
import com.cappleapple.instancednotinfinite.network.ManifestationRemovePayload;
import com.cappleapple.instancednotinfinite.network.ManifestationStartPayload;
import com.cappleapple.instancednotinfinite.network.DungeonCatalogPayload;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handle(ManifestationStartPayload payload) { ClientManifestationStore.start(payload); }
    public static void handle(ManifestationBlocksPayload payload) { ClientManifestationStore.add(payload); }
    public static void handle(ManifestationProgressPayload payload) { ClientManifestationStore.progress(payload); }
    public static void handle(ManifestationRemovePayload payload) { ClientManifestationStore.remove(payload.id()); }
    public static void handle(DungeonCatalogPayload payload) {
        ClientDungeonCatalog.update(payload.worldId(), payload.dungeons(), payload.structurePools());
    }
}
