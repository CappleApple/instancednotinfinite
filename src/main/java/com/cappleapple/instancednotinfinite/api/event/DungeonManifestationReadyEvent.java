package com.cappleapple.instancednotinfinite.api.event;

import com.cappleapple.instancednotinfinite.api.ManifestationView;
import com.cappleapple.instancednotinfinite.manifestation.DungeonManifestation;
import net.neoforged.bus.api.Event;

public final class DungeonManifestationReadyEvent extends Event {
    private final ManifestationView manifestation;

    public DungeonManifestationReadyEvent(DungeonManifestation manifestation) {
        this.manifestation = ManifestationView.from(manifestation);
    }

    public ManifestationView manifestation() { return manifestation; }
}
