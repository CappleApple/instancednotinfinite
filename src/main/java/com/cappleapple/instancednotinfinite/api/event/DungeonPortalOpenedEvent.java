package com.cappleapple.instancednotinfinite.api.event;

import com.cappleapple.instancednotinfinite.api.ManifestationView;
import com.cappleapple.instancednotinfinite.manifestation.DungeonManifestation;
import net.neoforged.bus.api.Event;

public final class DungeonPortalOpenedEvent extends Event {
    private final ManifestationView manifestation;

    public DungeonPortalOpenedEvent(DungeonManifestation manifestation) {
        this.manifestation = ManifestationView.from(manifestation);
    }

    public ManifestationView manifestation() { return manifestation; }
}
