package com.cappleapple.instancednotinfinite.compat;

import com.cappleapple.instancednotinfinite.manifestation.PortalRotation;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Separating-axis test for a transformed portal and an upright world-space entity box. */
public final class TransformedPortalShape {
    private static final Vec3[] WORLD_AXES = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
    private TransformedPortalShape() { }

    public static boolean intersects(Pose3dc pose, BlockPos anchor, int rotation,
            float width, float height, float depth, AABB other) {
        Vec3 center = pose.transformPosition(Vec3.atBottomCenterOf(anchor).add(0, 1.5, 0));
        Vec3[] edges = {
            pose.transformNormal(new Vec3(PortalRotation.tangentX(rotation), 0, PortalRotation.tangentZ(rotation)))
                .scale(Math.max(0, width * 0.5)),
            pose.transformNormal(new Vec3(0, 1, 0)).scale(Math.max(0, height * 0.5)),
            pose.transformNormal(new Vec3(PortalRotation.normalX(rotation), 0, PortalRotation.normalZ(rotation)))
                .scale(Math.max(1.0 / 16.0, depth * 0.5))
        };
        Vec3 delta = other.getCenter().subtract(center);
        Vec3 half = new Vec3(other.getXsize() * 0.5, other.getYsize() * 0.5, other.getZsize() * 0.5);
        for (Vec3 axis : WORLD_AXES) {
            if (separated(axis, delta, half, edges)) return false;
            for (Vec3 edge : edges) if (separated(axis.cross(edge), delta, half, edges)) return false;
        }
        for (int axis = 0; axis < 3; axis++) {
            if (separated(edges[axis].cross(edges[(axis + 1) % 3]), delta, half, edges)) return false;
        }
        return true;
    }

    private static boolean separated(Vec3 axis, Vec3 delta, Vec3 half, Vec3[] edges) {
        double radius = Math.abs(axis.x) * half.x + Math.abs(axis.y) * half.y + Math.abs(axis.z) * half.z;
        for (Vec3 edge : edges) radius += Math.abs(axis.dot(edge));
        return Math.abs(axis.dot(delta)) > radius + 1.0E-9;
    }
}
