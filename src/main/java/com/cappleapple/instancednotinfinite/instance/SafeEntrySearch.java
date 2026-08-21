package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

final class SafeEntrySearch {
    private SafeEntrySearch() {
    }

    static Optional<BlockPos> automatic(GenerationPlan plan, Predicate<BlockPos> safe) {
        BlockPos requested = plan.entryPosition();
        BoundingBox structure = plan.structureBounds();
        int minY = structure.minY();
        int maxY = structure.maxY() + 1;
        int horizontalRadius = Math.max(
            Math.max(Math.abs(requested.getX() - structure.minX()), Math.abs(structure.maxX() - requested.getX())),
            Math.max(Math.abs(requested.getZ() - structure.minZ()), Math.abs(structure.maxZ() - requested.getZ())));
        int verticalRadius = Math.max(Math.abs(requested.getY() - minY), Math.abs(maxY - requested.getY()));
        return search(requested, structure, minY, maxY, horizontalRadius, verticalRadius, safe);
    }

    static Optional<BlockPos> nearby(GenerationPlan plan, Predicate<BlockPos> safe) {
        return search(
            plan.entryPosition(), plan.envelopeBounds(),
            plan.entryPosition().getY() - 32, plan.entryPosition().getY() + 32,
            12, 32, safe);
    }

    private static Optional<BlockPos> search(
        BlockPos requested,
        BoundingBox horizontalBounds,
        int minY,
        int maxY,
        int horizontalRadius,
        int verticalRadius,
        Predicate<BlockPos> safe
    ) {
        for (int radius = 0; radius <= horizontalRadius; radius++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (radius > 0 && Math.abs(offsetX) != radius && Math.abs(offsetZ) != radius) {
                        continue;
                    }
                    int x = requested.getX() + offsetX;
                    int z = requested.getZ() + offsetZ;
                    if (x < horizontalBounds.minX() || x > horizontalBounds.maxX()
                        || z < horizontalBounds.minZ() || z > horizontalBounds.maxZ()) {
                        continue;
                    }
                    for (int distanceY = 0; distanceY <= verticalRadius; distanceY++) {
                        int upward = requested.getY() + distanceY;
                        if (upward >= minY && upward <= maxY) {
                            BlockPos candidate = new BlockPos(x, upward, z);
                            if (safe.test(candidate)) {
                                return Optional.of(candidate);
                            }
                        }
                        if (distanceY > 0) {
                            int downward = requested.getY() - distanceY;
                            if (downward >= minY && downward <= maxY) {
                                BlockPos candidate = new BlockPos(x, downward, z);
                                if (safe.test(candidate)) {
                                    return Optional.of(candidate);
                                }
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }
}
