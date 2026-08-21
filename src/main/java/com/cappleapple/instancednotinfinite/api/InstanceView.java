package com.cappleapple.instancednotinfinite.api;

import com.cappleapple.instancednotinfinite.instance.DungeonInstance;
import com.cappleapple.instancednotinfinite.instance.InstanceState;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public record InstanceView(
    UUID id,
    ResourceLocation dungeon,
    ResourceLocation dimension,
    ResourceLocation structure,
    ResourceLocation biome,
    long seed,
    InstanceState state,
    Set<UUID> assignedPlayers
) {
    /** Creates an immutable API snapshot from a live internal record. */
    public static InstanceView from(DungeonInstance instance) {
        return new InstanceView(
            instance.id().value(), ResourceLocation.parse(instance.definition().id()), instance.dimensionId(),
            instance.structureId(), instance.biomeId(), instance.seed(), instance.state(), instance.assignedPlayers());
    }
}
