package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.manifestation.ManifestationState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class ManifestationWorldRenderer {
    private ManifestationWorldRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        for (ClientManifestation value : ClientManifestationStore.values()) {
            if (!value.dimension().equals(minecraft.level.dimension().location())) continue;
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(
                value.origin().getX() + 0.5 - camera.x,
                value.origin().getY() + 1.5 - camera.y,
                value.origin().getZ() + 0.5 - camera.z);
            if (value.state() == ManifestationState.PORTAL_OPEN) {
                renderPortal(value, pose, event, minecraft.level.getGameTime() + partial, 1.0F, 0.0F);
            } else if (value.state() == ManifestationState.PORTAL_OPENING) {
                float elapsed = Math.min(1.0F, Math.max(0.0F,
                    (minecraft.level.getGameTime() + partial - value.stateChangedGameTime())
                        / value.portalGrowthDurationTicks()));
                float inverse = 1.0F - elapsed;
                float scale = 1.0F - inverse * inverse * inverse;
                renderPortal(value, pose, event, minecraft.level.getGameTime() + partial,
                    Math.max(0.001F, scale), 0.0F);
            } else if (value.state() == ManifestationState.CLOSING) {
                float elapsed = Math.min(1.0F, Math.max(0.0F,
                    (minecraft.level.getGameTime() + partial - value.stateChangedGameTime())
                        / value.portalCloseDurationTicks()));
                renderPortal(value, pose, event, minecraft.level.getGameTime() + partial, 1.0F, elapsed);
            } else if (value.state() == ManifestationState.COLLAPSING) {
                float collapse = Math.min(1.0F, Math.max(0.0F,
                    (minecraft.level.getGameTime() + partial - value.stateChangedGameTime()) / value.collapseDurationTicks()));
                float scale = (1.0F - collapse) * (1.0F - collapse);
                DungeonMiniatureRenderer.renderWorld(value, pose, Math.max(0.001F, scale), collapse * 720.0F);
            } else if (!value.state().terminal()) {
                float hover = (float)Math.sin((minecraft.level.getGameTime() + partial) * 0.04) * 0.08F;
                pose.translate(0.0, hover, 0.0);
                DungeonMiniatureRenderer.renderWorld(value, pose, 1.0F, (minecraft.level.getGameTime() + partial) * 0.35F);
            }
            pose.popPose();
        }
    }

    private static void renderPortal(
        ClientManifestation value,
        PoseStack pose,
        RenderLevelStageEvent event,
        double time,
        float growthScale,
        float collapseProgress
    ) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            PortalGeometryRenderer.render(
            pose, buffers, value.rotationDegrees(), value.portalWidth(), value.portalHeight(), value.portalDepth(),
            value.portalMinimumWidth(), value.portalMinimumHeight(), value.portalMinimumDepth(),
            value.portalInnerColor(), value.portalOuterColor(), growthScale, time,
            value.portalLifetimeFraction(), collapseProgress);
        buffers.endBatch(RenderType.debugQuads());
    }
}
