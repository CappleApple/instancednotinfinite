package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.compat.SableCoordinates;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import java.util.OptionalDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** World-stage rendering is outside Sable's block-entity render transform. Apply it exactly once. */
final class SableRenderCoordinates {
    private SableRenderCoordinates() { }

    private static Pose3dc pose(Level level, BlockPos anchor) {
        SubLevelAccess subLevel = SableCoordinates.subLevel(level, anchor);
        return subLevel instanceof ClientSubLevelAccess client ? client.renderPose()
            : subLevel == null ? null : subLevel.logicalPose();
    }

    static void translate(PoseStack stack, Level level, BlockPos anchor, Vec3 local, Vec3 camera) {
        Pose3dc pose = pose(level, anchor);
        Vec3 world = pose == null ? local : pose.transformPosition(local);
        stack.translate(world.x - camera.x, world.y - camera.y, world.z - camera.z);
        if (pose != null) {
            stack.mulPose(new Quaternionf(pose.orientation()));
            stack.scale((float)pose.scale().x(), (float)pose.scale().y(), (float)pose.scale().z());
        }
    }

    static OptionalDouble rayDistance(Level level, BlockPos anchor, Vec3 camera, Vec3 look,
            double centerX, double centerY, double centerZ, int rotation, float width, float height,
            float depth, double maximumDistance) {
        if (!SableCoordinates.available(level, anchor)) return OptionalDouble.empty();
        Pose3dc pose = pose(level, anchor);
        if (pose != null) {
            camera = pose.transformPositionInverse(camera);
            // Do not normalize: the ray parameter must remain a distance in world space, including scale.
            look = pose.transformNormalInverse(look);
        }
        return PortalTargetingMath.rayDistance(camera.x, camera.y, camera.z, look.x, look.y, look.z,
            centerX, centerY, centerZ, rotation, width, height, depth, maximumDistance);
    }
}
