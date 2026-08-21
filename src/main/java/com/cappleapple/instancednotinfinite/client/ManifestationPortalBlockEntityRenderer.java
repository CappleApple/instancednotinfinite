package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.content.ManifestationPortalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** Renders the full-size return endpoint inside a generated dungeon instance. */
public final class ManifestationPortalBlockEntityRenderer implements BlockEntityRenderer<ManifestationPortalBlockEntity> {
    public ManifestationPortalBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
        ManifestationPortalBlockEntity portal,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        if (portal.endpoint() != ManifestationPortalBlockEntity.Endpoint.RETURN) return;
        pose.pushPose();
        pose.translate(0.5, 1.5, 0.5);
        double time = (portal.getLevel() == null ? 0.0 : portal.getLevel().getGameTime()) + partialTick;
        PortalGeometryRenderer.render(
            pose, buffers, portal.rotationDegrees(), portal.portalWidth(), portal.portalHeight(), portal.portalDepth(),
            portal.portalMinimumWidth(), portal.portalMinimumHeight(), portal.portalMinimumDepth(),
            portal.portalInnerColor(), portal.portalOuterColor(), 1.0F, time, 1.0F, 0.0F);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ManifestationPortalBlockEntity portal) {
        double radius = Math.max(1.0, Math.max(portal.portalWidth(), portal.portalDepth()) * 0.5 + 0.2);
        return new AABB(portal.getBlockPos()).inflate(radius, Math.max(1.0, portal.portalHeight()), radius);
    }
}
