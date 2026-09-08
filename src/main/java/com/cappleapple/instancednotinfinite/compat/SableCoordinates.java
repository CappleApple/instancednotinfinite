package com.cappleapple.instancednotinfinite.compat;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Block access stays in plot space; distances, collisions and teleports use world space. */
public final class SableCoordinates {
    private SableCoordinates() { }

    public static SubLevelAccess subLevel(Level level, BlockPos anchor) {
        return level == null ? null : SableCompanion.INSTANCE.getContaining(level, anchor);
    }

    public static boolean available(Level level, BlockPos anchor) {
        return level != null && (!SableCompanion.INSTANCE.isInPlotGrid(level, anchor)
            || subLevel(level, anchor) != null);
    }

    public static Optional<UUID> subLevelId(Level level, BlockPos anchor) {
        return Optional.ofNullable(subLevel(level, anchor)).map(SubLevelAccess::getUniqueId);
    }

    public static boolean matches(Level level, BlockPos anchor, Optional<UUID> expected) {
        return available(level, anchor) && (expected.isEmpty() || expected.equals(subLevelId(level, anchor)));
    }

    public static Vec3 toWorld(Level level, Vec3 position) {
        SubLevelAccess subLevel = subLevel(level, BlockPos.containing(position));
        return subLevel == null ? position : subLevel.logicalPose().transformPosition(position);
    }

    public static Vec3 toLocal(Level level, BlockPos anchor, Vec3 position) {
        SubLevelAccess subLevel = subLevel(level, anchor);
        return subLevel == null ? position : subLevel.logicalPose().transformPositionInverse(position);
    }

    public static AABB worldBounds(Level level, BlockPos anchor, AABB local) {
        SubLevelAccess subLevel = subLevel(level, anchor);
        return subLevel == null ? local : transformBounds(local, subLevel.logicalPose());
    }

    private static AABB transformBounds(AABB bounds, Pose3dc pose) {
        BoundingBox3d result = new BoundingBox3d(bounds).transform(pose);
        return new AABB(result.minX(), result.minY(), result.minZ(), result.maxX(), result.maxY(), result.maxZ());
    }

    public static double distanceSquared(Level level, Vec3 first, Vec3 second) {
        return toWorld(level, first).distanceToSqr(toWorld(level, second));
    }

    public static int localYaw(Level level, BlockPos anchor, float worldYaw) {
        SubLevelAccess subLevel = subLevel(level, anchor);
        if (subLevel == null) return Math.round(worldYaw);
        Vec3 forward = subLevel.logicalPose().transformNormalInverse(Vec3.directionFromRotation(0, worldYaw));
        return yaw(forward);
    }

    public static int yaw(Vec3 forward) {
        return (int)Math.round(Math.toDegrees(Math.atan2(-forward.x, forward.z)));
    }
}
