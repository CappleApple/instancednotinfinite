package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Resolves the return portal behind the direction a player faces on instance arrival. */
public final class DestinationPortalPlacement {
    private DestinationPortalPlacement() {
    }

    public static BlockPos position(GenerationPlan plan, int distance) {
        return position(plan.entryPosition(), plan.entryYaw(), distance);
    }

    public static BlockPos position(BlockPos entry, float entryYaw, int distance) {
        double normalX = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.normalX(Math.round(entryYaw));
        double normalZ = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.normalZ(Math.round(entryYaw));
        return entry.offset((int)Math.round(-normalX * distance), 0, (int)Math.round(-normalZ * distance));
    }

    public static Direction facing(float entryYaw) {
        return Direction.fromYRot(entryYaw);
    }
}
