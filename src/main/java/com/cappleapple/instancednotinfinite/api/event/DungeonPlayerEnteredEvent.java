package com.cappleapple.instancednotinfinite.api.event;

import com.cappleapple.instancednotinfinite.api.InstanceView;
import com.cappleapple.instancednotinfinite.instance.DungeonInstance;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

public final class DungeonPlayerEnteredEvent extends Event {
    private final InstanceView instance;
    private final ServerPlayer player;

    public DungeonPlayerEnteredEvent(DungeonInstance instance, ServerPlayer player) {
        this.instance = InstanceView.from(instance);
        this.player = player;
    }

    public InstanceView instance() {
        return instance;
    }

    public ServerPlayer player() {
        return player;
    }
}
