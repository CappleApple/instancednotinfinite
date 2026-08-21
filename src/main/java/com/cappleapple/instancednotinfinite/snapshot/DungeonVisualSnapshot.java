package com.cappleapple.instancednotinfinite.snapshot;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Immutable, render-oriented view of the exact generated instance. */
public record DungeonVisualSnapshot(
    ResourceLocation dungeonId,
    ResourceLocation biomeId,
    long seed,
    BoundingBox bounds,
    List<VisualBlock> blocks
) {
    public DungeonVisualSnapshot {
        blocks = List.copyOf(blocks);
    }
}
