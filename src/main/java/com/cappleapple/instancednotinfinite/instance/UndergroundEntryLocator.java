package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Selects a deterministic-random walkable authored interior and a route into its stone shell. */
final class UndergroundEntryLocator {
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private UndergroundEntryLocator() {
    }

    static Optional<AutomaticEntryLocator.Approach> locate(
        ServerLevel level,
        GenerationPlan plan,
        List<BoundingBox> authoredPieceBounds,
        AutomaticApproachBuilder.Settings settings
    ) {
        Candidate selected = null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (BoundingBox piece : authoredPieceBounds) {
            int minY = Math.max(piece.minY(), level.getMinBuildHeight() + 1);
            int maxY = Math.min(piece.maxY(), level.getMaxBuildHeight() - 2);
            for (int y = minY; y <= maxY; y++) {
                for (int x = piece.minX(); x <= piece.maxX(); x++) {
                    for (int z = piece.minZ(); z <= piece.maxZ(); z++) {
                        cursor.set(x, y, z);
                        BlockPos access = cursor.immutable();
                        if (!isWalkable(level, access) || !connectsToWalkableInterior(level, access, authoredPieceBounds)) {
                            continue;
                        }
                        for (Direction outward : HORIZONTAL_DIRECTIONS) {
                            if (!routeFits(level, plan, access, outward, authoredPieceBounds, settings)) {
                                continue;
                            }
                            long rank = randomRank(plan.seed(), access, outward);
                            if (selected == null || Long.compareUnsigned(rank, selected.rank()) < 0) {
                                selected = new Candidate(access, outward, rank);
                            }
                        }
                    }
                }
            }
        }
        if (selected == null) {
            return Optional.empty();
        }
        return Optional.of(new AutomaticEntryLocator.Approach(
            selected.access(), selected.access().relative(selected.outward()), selected.outward(), true));
    }

    private static boolean routeFits(
        ServerLevel level,
        GenerationPlan plan,
        BlockPos access,
        Direction outward,
        List<BoundingBox> pieces,
        AutomaticApproachBuilder.Settings settings
    ) {
        BlockPos platformCenter = access.relative(outward, settings.distance() + 1);
        BoundingBox guaranteed = plan.guaranteedBounds();
        int radius = settings.platformRadius();
        if (platformCenter.getX() - radius < guaranteed.minX()
            || platformCenter.getX() + radius > guaranteed.maxX()
            || platformCenter.getZ() - radius < guaranteed.minZ()
            || platformCenter.getZ() + radius > guaranteed.maxZ()
            || platformCenter.getY() <= guaranteed.minY()
            || platformCenter.getY() + settings.platformClearanceHeight() - 1 > guaranteed.maxY()) {
            return false;
        }

        Direction sideways = outward.getClockWise();
        for (int forward = -radius; forward <= Math.max(radius, settings.destinationPortalBehindEntryBlocks()); forward++) {
            for (int side = -radius; side <= radius; side++) {
                BlockPos platform = platformCenter.relative(outward, forward).relative(sideways, side);
                if (insideAnyPiece(pieces, platform) || insideAnyPiece(pieces, platform.below())) {
                    return false;
                }
            }
        }

        boolean reachesEncasingTerrain = false;
        for (int step = 1; step <= settings.distance() + 1; step++) {
            BlockPos route = access.relative(outward, step);
            if (!insideAnyPiece(pieces, route)
                && isSolid(level, route)
                && isSolid(level, route.above())) {
                reachesEncasingTerrain = true;
                break;
            }
        }
        return reachesEncasingTerrain;
    }

    private static boolean connectsToWalkableInterior(
        ServerLevel level,
        BlockPos access,
        List<BoundingBox> pieces
    ) {
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos adjacent = access.relative(direction);
            if (insideAnyPiece(pieces, adjacent) && isWalkable(level, adjacent)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWalkable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
            && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
            && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
            && level.getFluidState(feet).isEmpty()
            && level.getFluidState(feet.above()).isEmpty();
    }

    private static boolean isSolid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean insideAnyPiece(List<BoundingBox> pieces, BlockPos pos) {
        for (BoundingBox piece : pieces) {
            if (piece.isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    private static long randomRank(long seed, BlockPos position, Direction direction) {
        long value = seed ^ position.asLong() ^ ((long)direction.ordinal() * 0x9E3779B97F4A7C15L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record Candidate(BlockPos access, Direction outward, long rank) {
    }
}
