package com.cappleapple.instancednotinfinite.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/** Shared cached-mesh presentation used by world holograms and catalyst item previews. */
public final class DungeonMiniatureRenderer {
    private static final float ICON_PITCH_DEGREES = 30.0F;
    private static final float ICON_YAW_DEGREES = 135.0F;
    private static final double ICON_OCCUPIED_FRACTION = 0.86;
    private static final float ITEM_YAW_DEGREES = 35.0F;

    private DungeonMiniatureRenderer() {
    }

    public static void renderWorld(ClientManifestation value, PoseStack pose, float collapseScale, float rotationDegrees) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(rotationDegrees));
        float fit = Math.min(
            value.maximumWidth() / value.visualSizeX(),
            Math.min(value.maximumHeight() / value.visualSizeY(), value.maximumDepth() / value.visualSizeZ()));
        pose.scale(fit * collapseScale, fit * collapseScale, fit * collapseScale);
        pose.translate(-value.visualCenterX(), -value.visualCenterY(), -value.visualCenterZ());
        renderBlocks(value, pose, value.progress(), true);
        pose.popPose();
    }

    public static void renderIcon(ClientManifestation value, PoseStack pose) {
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.XP.rotationDegrees(ICON_PITCH_DEGREES));
        pose.mulPose(Axis.YP.rotationDegrees(ICON_YAW_DEGREES));
        float fit = (float)MiniatureProjection.fit(
            value.visualSizeX(), value.visualSizeY(), value.visualSizeZ(),
            ICON_PITCH_DEGREES, ICON_YAW_DEGREES,
            ICON_OCCUPIED_FRACTION, ICON_OCCUPIED_FRACTION);
        pose.scale(fit, fit, fit);
        pose.translate(-value.visualCenterX(), -value.visualCenterY(), -value.visualCenterZ());
        renderBlocks(value, pose, 1.0F, false);
        pose.popPose();
    }

    /** Upright presentation for hands, dropped items, item frames, and other non-GUI contexts. */
    public static void renderItem(ClientManifestation value, PoseStack pose) {
        renderItem(value, pose, 1.0F, 0.0F);
    }

    public static void renderItem(
        ClientManifestation value,
        PoseStack pose,
        float transitionScale,
        float transitionRotationDegrees
    ) {
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.scale(transitionScale, transitionScale, transitionScale);
        float yaw = ITEM_YAW_DEGREES + transitionRotationDegrees;
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        float horizontalSpan = rotatedHorizontalSpan(value, yaw);
        float fit = Math.min(0.78F / horizontalSpan, 0.78F / value.visualSizeY());
        pose.scale(fit, fit, fit);
        pose.translate(-value.visualCenterX(), -value.visualCenterY(), -value.visualCenterZ());
        renderBlocks(value, pose, 1.0F, false);
        pose.popPose();
    }

    private static float rotatedHorizontalSpan(ClientManifestation value, float yawDegrees) {
        double radians = Math.toRadians(yawDegrees);
        return (float)(Math.abs(Math.cos(radians)) * value.visualSizeX()
            + Math.abs(Math.sin(radians)) * value.visualSizeZ());
    }

    private static void renderBlocks(ClientManifestation value, PoseStack pose, float progress, boolean hologramTint) {
        if (hologramTint) {
            RenderSystem.setShaderColor(0.72F, 0.9F, 1.0F, value.structureAlpha());
        } else {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        try {
            HologramMeshCache.render(value, pose, progress);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
