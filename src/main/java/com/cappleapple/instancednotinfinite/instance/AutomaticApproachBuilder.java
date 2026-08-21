package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Builds the configured arrival platform and a level path back to a detected structure front. */
final class AutomaticApproachBuilder {
    private static final int UPDATE_FLAGS = 3;

    private AutomaticApproachBuilder() {
    }

    static BuiltApproach build(
        ServerLevel level,
        GenerationPlan plan,
        AutomaticEntryLocator.Approach approach
    ) throws InstanceOperationException {
        return build(level, plan, approach, Settings.fromConfig());
    }

    static BuiltApproach build(
        ServerLevel level,
        GenerationPlan plan,
        AutomaticEntryLocator.Approach approach,
        Settings settings
    ) throws InstanceOperationException {
        BlockState platform = resolveSolidBlock(level, settings.platformBlock(), "approach.platformBlock");
        BlockState path = resolveSolidBlock(level, settings.pathBlock(), "approach.pathBlock");
        Direction outward = approach.outward();
        Direction sideways = outward.getClockWise();
        BlockPos platformCenter = approach.exterior().relative(outward, settings.distance());
        validateFits(plan, platformCenter, settings.platformRadius(), settings.platformClearanceHeight());

        int left = (settings.pathWidth() - 1) / 2;
        int right = settings.pathWidth() / 2;
        for (int step = 0; step <= settings.distance(); step++) {
            BlockPos center = approach.exterior().relative(outward, step);
            for (int offset = -left; offset <= right; offset++) {
                BlockPos feet = center.relative(sideways, offset);
                setFloorAndClear(level, feet, path, settings.pathClearanceHeight());
            }
        }

        int radius = settings.platformRadius();
        for (int outwardOffset = -radius; outwardOffset <= radius; outwardOffset++) {
            for (int sideOffset = -radius; sideOffset <= radius; sideOffset++) {
                BlockPos feet = platformCenter.relative(outward, outwardOffset).relative(sideways, sideOffset);
                setFloorAndClear(level, feet, platform, settings.platformClearanceHeight());
            }
        }
        for (int offset = radius + 1; offset <= settings.destinationPortalBehindEntryBlocks(); offset++) {
            setFloorAndClear(
                level, platformCenter.relative(outward, offset), platform, settings.platformClearanceHeight());
        }
        BlockPos destinationPortal = platformCenter.relative(outward, settings.destinationPortalBehindEntryBlocks());
        validateDestinationPortalFits(plan, destinationPortal);
        setFloorAndClear(level, destinationPortal, platform, settings.platformClearanceHeight());
        return new BuiltApproach(platformCenter.immutable(), yawFacing(outward.getOpposite()));
    }

    private static void setFloorAndClear(ServerLevel level, BlockPos feet, BlockState floor, int clearanceHeight) {
        level.setBlock(feet.below(), floor, UPDATE_FLAGS);
        for (int offsetY = 0; offsetY < clearanceHeight; offsetY++) {
            BlockPos clear = feet.above(offsetY);
            if (!level.getBlockState(clear).isAir()) {
                level.setBlock(clear, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
            }
        }
    }

    private static BlockState resolveSolidBlock(ServerLevel level, String rawId, String option) throws InstanceOperationException {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            throw new InstanceOperationException(option + " is not a valid block ID: " + rawId);
        }
        Registry<Block> blocks = level.registryAccess().registryOrThrow(Registries.BLOCK);
        Block block = blocks.get(id);
        if (block == null || block == Blocks.AIR) {
            throw new InstanceOperationException(option + " does not resolve to a solid block: " + rawId);
        }
        BlockState state = block.defaultBlockState();
        if (state.getCollisionShape(level, BlockPos.ZERO).isEmpty()) {
            throw new InstanceOperationException(option + " must have a non-empty collision shape: " + rawId);
        }
        return state;
    }

    private static void validateFits(
        GenerationPlan plan,
        BlockPos center,
        int radius,
        int platformClearanceHeight
    ) throws InstanceOperationException {
        BoundingBox bounds = plan.guaranteedBounds();
        if (center.getX() - radius < bounds.minX() || center.getX() + radius > bounds.maxX()
            || center.getZ() - radius < bounds.minZ() || center.getZ() + radius > bounds.maxZ()) {
            throw new InstanceOperationException(
                "Configured automatic approach does not fit terrain.horizontalPadding; increase it or reduce approach.distance/platformRadius");
        }
        if (center.getY() <= GenerationPlan.MIN_TERRAIN_Y
            || center.getY() + platformClearanceHeight - 1 > GenerationPlan.MAX_TERRAIN_Y) {
            throw new InstanceOperationException("Configured automatic approach does not fit the instance build height");
        }
    }

    private static void validateDestinationPortalFits(GenerationPlan plan, BlockPos portal) throws InstanceOperationException {
        BoundingBox bounds = plan.guaranteedBounds();
        if (portal.getX() < bounds.minX() || portal.getX() > bounds.maxX()
            || portal.getZ() < bounds.minZ() || portal.getZ() > bounds.maxZ()) {
            throw new InstanceOperationException(
                "Configured destinationPortalBehindEntryBlocks places the return portal outside the guaranteed terrain bounds");
        }
    }

    private static float yawFacing(Direction direction) {
        return switch (direction) {
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }

    record BuiltApproach(BlockPos spawn, float yaw) {
    }

    record Settings(
        int distance,
        int platformRadius,
        int pathWidth,
        int pathClearanceHeight,
        int platformClearanceHeight,
        String platformBlock,
        String pathBlock,
        int destinationPortalBehindEntryBlocks
    ) {
        Settings(
            int distance,
            int platformRadius,
            int pathWidth,
            String platformBlock,
            String pathBlock,
            int destinationPortalBehindEntryBlocks
        ) {
            this(distance, platformRadius, pathWidth, 3, 4, platformBlock, pathBlock, destinationPortalBehindEntryBlocks);
        }

        static Settings fromConfig() {
            return new Settings(
                ServerConfig.INSTANCE.approachDistance.get(),
                ServerConfig.INSTANCE.approachPlatformRadius.get(),
                ServerConfig.INSTANCE.approachPathWidth.get(),
                ServerConfig.INSTANCE.approachPathClearanceHeight.get(),
                ServerConfig.INSTANCE.approachPlatformClearanceHeight.get(),
                ServerConfig.INSTANCE.approachPlatformBlock.get(),
                ServerConfig.INSTANCE.approachPathBlock.get(),
                ServerConfig.INSTANCE.destinationPortalBehindEntryBlocks.get());
        }
    }
}
