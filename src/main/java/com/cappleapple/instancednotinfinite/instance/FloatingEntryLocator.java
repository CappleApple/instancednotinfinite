package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Finds a walkable, sky-exposed 3x3 authored landing near an edge, allowing the route to be carved. */
final class FloatingEntryLocator {
    private FloatingEntryLocator() {
    }

    static Optional<AutomaticEntryLocator.Approach> locate(ServerLevel level, GenerationPlan plan,
        List<BoundingBox> pieces, AutomaticApproachBuilder.Settings settings) {
        BoundingBox bounds = plan.structureBounds();
        AutomaticEntryLocator.Approach best = null;
        long bestScore = Long.MAX_VALUE;
        for (int x = bounds.minX() + 1; x < bounds.maxX(); x++) {
            for (int z = bounds.minZ() + 1; z < bounds.maxZ(); z++) {
                // A surface landing must be above the highest obstruction in this column.
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y < bounds.minY() + 1 || y > bounds.maxY() + 1) continue;
                BlockPos center = new BlockPos(x, y, z);
                if (!landing(level, center, pieces, settings.pathClearanceHeight())) continue;
                for (Direction outward : Direction.Plane.HORIZONTAL) {
                    BlockPos exterior = switch (outward) {
                        case NORTH -> new BlockPos(x, y, bounds.minZ() - 1);
                        case SOUTH -> new BlockPos(x, y, bounds.maxZ() + 1);
                        case WEST -> new BlockPos(bounds.minX() - 1, y, z);
                        case EAST -> new BlockPos(bounds.maxX() + 1, y, z);
                        default -> throw new IllegalStateException("Non-horizontal direction");
                    };
                    int distance = center.distManhattan(exterior);
                    // Prefer the nearest edge, but allow crossing a supported wall or parapet to reach it.
                    long score = (long)distance * 1024 + y - bounds.minY();
                    if (score >= bestScore || !routeFits(level, plan, center, exterior, outward, settings)) continue;
                    best = new AutomaticEntryLocator.Approach(center, exterior, outward, true);
                    bestScore = score;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean landing(ServerLevel level, BlockPos center, List<BoundingBox> pieces, int clearance) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos feet = center.offset(dx, 0, dz);
                if (pieces.stream().noneMatch(piece -> piece.isInside(feet.below()))
                    || !solidFloor(level, feet) || !level.canSeeSky(feet) || !clear(level, feet, clearance)) return false;
            }
        }
        return true;
    }

    private static boolean solidFloor(ServerLevel level, BlockPos feet) {
        var floor = level.getBlockState(feet.below());
        return floor.isFaceSturdy(level, feet.below(), Direction.UP)
            && !floor.is(Blocks.MAGMA_BLOCK) && !floor.is(Blocks.CAMPFIRE) && !floor.is(Blocks.SOUL_CAMPFIRE)
            && !floor.is(Blocks.CACTUS) && !floor.is(Blocks.POWDER_SNOW);
    }

    private static boolean clear(ServerLevel level, BlockPos feet, int height) {
        if (feet.getY() + height > level.getMaxBuildHeight()) return false;
        for (int dy = 0; dy < height; dy++) {
            BlockPos pos = feet.above(dy);
            var state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty()
                || state.is(net.minecraft.tags.BlockTags.FIRE) || state.is(Blocks.WITHER_ROSE)) return false;
        }
        return true;
    }

    private static boolean routeFits(ServerLevel level, GenerationPlan plan, BlockPos center, BlockPos exterior,
        Direction outward, AutomaticApproachBuilder.Settings settings) {
        int clearance = Math.max(settings.pathClearanceHeight(), settings.platformClearanceHeight());
        if (center.getY() <= level.getMinBuildHeight() || center.getY() + clearance > level.getMaxBuildHeight()) return false;
        int toPlatform = center.distManhattan(exterior) + settings.distance();
        int end = toPlatform + Math.max(settings.platformRadius(), settings.destinationPortalBehindEntryBlocks());
        BoundingBox guaranteed = plan.guaranteedBounds();
        Direction side = outward.getClockWise();
        for (int step = 0; step <= end; step++) {
            int halfWidth = Math.abs(step - toPlatform) <= settings.platformRadius()
                ? Math.max(settings.platformRadius(), settings.pathWidth() / 2) : settings.pathWidth() / 2;
            for (int offset = -halfWidth; offset <= halfWidth; offset++) {
                BlockPos feet = center.relative(outward, step).relative(side, offset);
                if (feet.getX() < guaranteed.minX() || feet.getX() > guaranteed.maxX()
                    || feet.getZ() < guaranteed.minZ() || feet.getZ() > guaranteed.maxZ()) return false;
            }
        }
        return true;
    }
}
