package com.cappleapple.instancednotinfinite.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Builds the server interaction volume from the same dimensions and orientation as the rendered portal. */
public final class PortalInteractionShape {
    private PortalInteractionShape() {
    }

    public static AABB bounds(BlockPos pos, int rotationDegrees, float width, float height, float depth) {
        PortalShapeMath.Bounds bounds = PortalShapeMath.bounds(
            pos.getX(), pos.getY(), pos.getZ(), rotationDegrees,
            width, height, depth);
        return new AABB(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    public static VoxelShape voxelShape(BlockPos pos, int rotationDegrees, float width, float height, float depth) {
        return Shapes.create(bounds(pos, rotationDegrees, width, height, depth)
            .move(-pos.getX(), -pos.getY(), -pos.getZ()));
    }

    public static boolean intersects(
        BlockPos pos,
        int rotationDegrees,
        float width,
        float height,
        float depth,
        AABB other
    ) {
        return PortalShapeMath.intersects(
            pos.getX(), pos.getY(), pos.getZ(), rotationDegrees, width, height, depth,
            other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
    }
}
