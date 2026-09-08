package com.cappleapple.instancednotinfinite.compat;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTest;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;


@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
@net.neoforged.neoforge.gametest.GameTestHolder("instancednotinfinite_sable")
public final class TransformedPortalShapeGameTests {
    @GameTest(templateNamespace = "instancednotinfinite_integration", template = "empty")
    public static void translatedRotatedTiltedAndScaledVolumesUseWorldCoordinates(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(28_000_000, 70, -28_000_000);
        for (double yaw : new double[]{0, 0.6, Math.PI / 2, 2.8}) {
            Pose3d pose = new Pose3d();
            pose.rotationPoint().set(anchor.getX() + 0.5, anchor.getY() + 1.5, anchor.getZ() + 0.5);
            pose.position().set(25, 110, -42);
            pose.orientation().rotationXYZ(0.4, yaw, 0.25);
            pose.scale().set(1.3, 0.8, 1.6);
            Vec3 center = new Vec3(25, 110, -42);
            helper.assertTrue(TransformedPortalShape.intersects(pose, anchor, 37, 2, 3, 0.2F,
                new AABB(center, center).inflate(0.1)), "Portal center did not intersect");
            Vec3 outside = center.add(pose.transformNormal(new Vec3(0, 0, 8)));
            helper.assertFalse(TransformedPortalShape.intersects(pose, anchor, 37, 2, 3, 0.2F,
                new AABB(outside, outside).inflate(0.2)), "Remote box intersected portal");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "instancednotinfinite_integration", template = "empty")
    public static void rotatedPortalRejectsEmptyCornerOfBroadphaseBounds(GameTestHelper helper) {
        Pose3d pose = new Pose3d();
        pose.orientation().rotationY(Math.PI / 4);
        pose.rotationPoint().set(0.5, 1.5, 0.5);
        pose.position().set(0.5, 1.5, 0.5);
        helper.assertFalse(TransformedPortalShape.intersects(pose, BlockPos.ZERO, 0, 2, 3, 0,
            new AABB(1.15, 1, 1.15, 1.25, 2, 1.25)), "Empty corner of broadphase bounds activated portal");
        helper.assertTrue(TransformedPortalShape.intersects(pose, BlockPos.ZERO, 0, 2, 3, 0,
            new AABB(0.45, 1, 0.45, 0.55, 2, 0.55)), "Portal center missed");
        helper.succeed();
    }
}
