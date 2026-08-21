package com.cappleapple.instancednotinfinite.api.event;

import com.cappleapple.instancednotinfinite.api.InstanceView;
import com.cappleapple.instancednotinfinite.instance.DungeonInstance;
import net.neoforged.bus.api.Event;

public final class DungeonCompletedEvent extends Event {
    private final InstanceView instance;

    public DungeonCompletedEvent(DungeonInstance instance) {
        this.instance = InstanceView.from(instance);
    }

    public InstanceView instance() {
        return instance;
    }
}
