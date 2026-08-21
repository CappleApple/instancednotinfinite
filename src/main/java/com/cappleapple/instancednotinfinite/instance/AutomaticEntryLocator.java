package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Finds the authored front and its dry exterior approach for an automatic surface structure. */
final class AutomaticEntryLocator {
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private AutomaticEntryLocator() {
    }

    static Optional<Approach> locate(ServerLevel level, GenerationPlan plan) {
        List<ApproachTarget> targets = orderedTargets(level, plan);
        return targets.isEmpty() ? Optional.empty() : Optional.of(targets.getFirst().approach());
    }

    /** Compatibility search used by focused entry safety tests and non-building callers. */
    static Optional<BlockPos> find(ServerLevel level, GenerationPlan plan, Predicate<BlockPos> safe) {
        for (ApproachTarget target : orderedTargets(level, plan)) {
            Optional<BlockPos> found = safeOutsideNear(plan, target.approach().exterior(), safe);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static List<ApproachTarget> orderedTargets(ServerLevel level, GenerationPlan plan) {
        List<ApproachTarget> targets = accessTargets(level, plan);
        targets.addAll(openBoundaryTargets(level, plan));
        targets.addAll(fallbackTargets(plan));
        targets.sort(Comparator.comparingInt(ApproachTarget::score));
        return targets;
    }

    private static List<ApproachTarget> accessTargets(ServerLevel level, GenerationPlan plan) {
        BoundingBox structure = plan.structureBounds();
        int scanMinY = Math.max(structure.minY(), plan.terrainSurfaceY() - 8);
        int scanMaxY = Math.min(structure.maxY(), plan.terrainSurfaceY() + 16);
        List<ApproachTarget> targets = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = scanMinY; y <= scanMaxY; y++) {
            for (int x = structure.minX(); x <= structure.maxX(); x++) {
                for (int z = structure.minZ(); z <= structure.maxZ(); z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!isLowerAccessBlock(state)) {
                        continue;
                    }
                    BlockPos access = cursor.immutable();
                    Direction facing = accessFacing(state);
                    addAccessTarget(targets, plan, structure, access, facing);
                    addAccessTarget(targets, plan, structure, access, facing.getOpposite());
                }
            }
        }
        targets.sort(Comparator.comparingInt(ApproachTarget::score));
        return targets.size() > 64 ? new ArrayList<>(targets.subList(0, 64)) : targets;
    }

    private static void addAccessTarget(
        List<ApproachTarget> targets,
        GenerationPlan plan,
        BoundingBox structure,
        BlockPos access,
        Direction outward
    ) {
        BlockPos exterior = exteriorTarget(structure, access, outward);
        targets.add(new ApproachTarget(
            new Approach(access, exterior, outward, true),
            score(plan, structure, access, outward)));
    }

    private static List<ApproachTarget> openBoundaryTargets(ServerLevel level, GenerationPlan plan) {
        BoundingBox structure = plan.structureBounds();
        int scanMinY = Math.max(structure.minY(), plan.terrainSurfaceY() - 8);
        int scanMaxY = Math.min(structure.maxY(), plan.terrainSurfaceY() + 16);
        List<ApproachTarget> targets = new ArrayList<>();
        for (Direction outward : HORIZONTAL_DIRECTIONS) {
            int length = outward.getAxis() == Direction.Axis.Z ? structure.getXSpan() : structure.getZSpan();
            for (int along = 0; along < length; along++) {
                for (int y = scanMinY; y <= scanMaxY; y++) {
                    BlockPos boundary = boundaryPosition(structure, outward, along, y);
                    for (int depth = 0; depth <= 3; depth++) {
                        BlockPos access = boundary.relative(outward.getOpposite(), depth);
                        if (!isOpenStandingSpace(level, access)) {
                            continue;
                        }
                        int openingScore = 100_000_000 + score(plan, structure, access, outward);
                        targets.add(new ApproachTarget(
                            new Approach(access, exteriorTarget(structure, access, outward), outward, true),
                            openingScore));
                        break;
                    }
                }
            }
        }
        targets.sort(Comparator.comparingInt(ApproachTarget::score));
        return targets.size() > 64 ? new ArrayList<>(targets.subList(0, 64)) : targets;
    }

    private static boolean isLowerAccessBlock(BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            return !state.hasProperty(DoorBlock.HALF) || state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
        }
        return state.getBlock() instanceof FenceGateBlock;
    }

    private static Direction accessFacing(BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            return state.getValue(DoorBlock.FACING);
        }
        return state.getValue(FenceGateBlock.FACING);
    }

    private static boolean isOpenStandingSpace(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
            && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
            && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
            && level.getFluidState(feet).isEmpty()
            && level.getFluidState(feet.above()).isEmpty();
    }

    private static int score(GenerationPlan plan, BoundingBox structure, BlockPos access, Direction direction) {
        int edgeDistance = switch (direction) {
            case NORTH -> access.getZ() - structure.minZ();
            case SOUTH -> structure.maxZ() - access.getZ();
            case WEST -> access.getX() - structure.minX();
            case EAST -> structure.maxX() - access.getX();
            default -> Integer.MAX_VALUE / 4;
        };
        int centerDistance = direction.getAxis() == Direction.Axis.Z
            ? Math.abs(access.getX() - (structure.minX() + structure.getXSpan() / 2))
            : Math.abs(access.getZ() - (structure.minZ() + structure.getZSpan() / 2));
        int levelDistance = Math.abs(access.getY() - (plan.terrainSurfaceY() + 1));
        return levelDistance * 65_536 + Math.max(0, edgeDistance) * 256 + centerDistance;
    }

    private static BlockPos exteriorTarget(BoundingBox structure, BlockPos access, Direction direction) {
        return switch (direction) {
            case NORTH -> new BlockPos(access.getX(), access.getY(), structure.minZ() - 1);
            case SOUTH -> new BlockPos(access.getX(), access.getY(), structure.maxZ() + 1);
            case WEST -> new BlockPos(structure.minX() - 1, access.getY(), access.getZ());
            case EAST -> new BlockPos(structure.maxX() + 1, access.getY(), access.getZ());
            default -> throw new IllegalArgumentException("Expected a horizontal direction");
        };
    }

    private static BlockPos boundaryPosition(BoundingBox structure, Direction direction, int along, int y) {
        return switch (direction) {
            case NORTH -> new BlockPos(structure.minX() + along, y, structure.minZ());
            case SOUTH -> new BlockPos(structure.minX() + along, y, structure.maxZ());
            case WEST -> new BlockPos(structure.minX(), y, structure.minZ() + along);
            case EAST -> new BlockPos(structure.maxX(), y, structure.minZ() + along);
            default -> throw new IllegalArgumentException("Expected a horizontal direction");
        };
    }

    private static List<ApproachTarget> fallbackTargets(GenerationPlan plan) {
        BoundingBox structure = plan.structureBounds();
        int centerX = structure.minX() + structure.getXSpan() / 2;
        int centerZ = structure.minZ() + structure.getZSpan() / 2;
        int y = plan.terrainSurfaceY() + 1;
        List<ApproachTarget> targets = new ArrayList<>();
        int score = Integer.MAX_VALUE - 4;
        for (Direction outward : HORIZONTAL_DIRECTIONS) {
            BlockPos exterior = switch (outward) {
                case NORTH -> new BlockPos(centerX, y, structure.minZ() - 1);
                case SOUTH -> new BlockPos(centerX, y, structure.maxZ() + 1);
                case WEST -> new BlockPos(structure.minX() - 1, y, centerZ);
                case EAST -> new BlockPos(structure.maxX() + 1, y, centerZ);
                default -> throw new IllegalArgumentException("Expected a horizontal direction");
            };
            targets.add(new ApproachTarget(new Approach(exterior, exterior, outward, false), score++));
        }
        return targets;
    }

    private static Optional<BlockPos> safeOutsideNear(
        GenerationPlan plan,
        BlockPos target,
        Predicate<BlockPos> safe
    ) {
        BoundingBox structure = plan.structureBounds();
        BoundingBox guaranteed = plan.guaranteedBounds();
        for (int radius = 0; radius <= 8; radius++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (radius > 0 && Math.abs(offsetX) != radius && Math.abs(offsetZ) != radius) {
                        continue;
                    }
                    int x = target.getX() + offsetX;
                    int z = target.getZ() + offsetZ;
                    if (x < guaranteed.minX() || x > guaranteed.maxX()
                        || z < guaranteed.minZ() || z > guaranteed.maxZ()
                        || insideHorizontalFootprint(structure, x, z)) {
                        continue;
                    }
                    for (int distanceY = 0; distanceY <= 8; distanceY++) {
                        BlockPos upward = new BlockPos(x, target.getY() + distanceY, z);
                        if (safe.test(upward)) {
                            return Optional.of(upward);
                        }
                        if (distanceY > 0) {
                            BlockPos downward = new BlockPos(x, target.getY() - distanceY, z);
                            if (safe.test(downward)) {
                                return Optional.of(downward);
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean insideHorizontalFootprint(BoundingBox structure, int x, int z) {
        return x >= structure.minX() && x <= structure.maxX()
            && z >= structure.minZ() && z <= structure.maxZ();
    }

    record Approach(BlockPos access, BlockPos exterior, Direction outward, boolean detected) {
    }

    private record ApproachTarget(Approach approach, int score) {
    }
}
