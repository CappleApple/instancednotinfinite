package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.content.DungeonDisplayName;
import com.cappleapple.instancednotinfinite.content.ManifestationPortalBlockEntity;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationState;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Shared camera target and display content used by both Jade and the built-in portal panel. */
public record PortalTooltipTarget(
    Kind kind,
    ResourceLocation dungeonId,
    ClientManifestation manifestation,
    BlockPos anchor,
    Vec3 hitLocation
) {
    private static final float LOADING_TARGET_PADDING = 0.5F;
    private static final float PORTAL_TARGET_PADDING = 0.16F;

    public static Optional<PortalTooltipTarget> find(Minecraft minecraft, double time) {
        Optional<PortalTooltipTarget> loading = targetedHologram(minecraft, time);
        return loading.isPresent() ? loading : targetedPortal(minecraft);
    }

    public String displayName() {
        return DungeonDisplayName.fromPath(dungeonId.getPath());
    }

    public String detailText() {
        if (kind == Kind.LOADING) {
            return "Loading: " + LoadingProgressMath.percentage(manifestation.animationProgress()) + "%";
        }
        return countdownText(manifestation);
    }

    public float loadingProgress() {
        return kind == Kind.LOADING ? manifestation.animationProgress() : 1.0F;
    }

    private static Optional<PortalTooltipTarget> targetedHologram(Minecraft minecraft, double time) {
        var camera = minecraft.gameRenderer.getMainCamera();
        var cameraPosition = camera.getPosition();
        Vector3f look = camera.getLookVector();
        double maximumDistance = ServerConfig.INSTANCE.portalHudDistance.get();
        PortalTooltipTarget closest = null;
        double closestDistance = maximumDistance + 1.0;
        for (ClientManifestation value : ClientManifestationStore.values()) {
            if (!value.dimension().equals(minecraft.level.dimension().location())) continue;
            if (value.state() != ManifestationState.PREPARING
                && value.state() != ManifestationState.GENERATING
                && value.state() != ManifestationState.MANIFESTING
                && value.state() != ManifestationState.FINALIZING) continue;
            boolean particleCue = value.blockCount() == 0;
            float width;
            float height;
            float depth;
            double centerY;
            int renderedRotation;
            if (particleCue) {
                float radius = Math.max(0.5F, value.preparationParticleRadius());
                height = Math.max(1.0F, Math.max(value.maximumHeight(), value.portalHeight()));
                width = radius * 2.0F;
                depth = radius * 2.0F;
                centerY = value.origin().getY() + height * 0.5;
                renderedRotation = 0;
            } else {
                float fit = Math.min(
                    value.maximumWidth() / value.visualSizeX(),
                    Math.min(value.maximumHeight() / value.visualSizeY(), value.maximumDepth() / value.visualSizeZ()));
                if (!(fit > 0.0F)) continue;
                width = value.visualSizeX() * fit + LOADING_TARGET_PADDING * 2.0F;
                height = value.visualSizeY() * fit + LOADING_TARGET_PADDING * 2.0F;
                depth = value.visualSizeZ() * fit + LOADING_TARGET_PADDING * 2.0F;
                float hover = (float)Math.sin(time * 0.04) * 0.08F;
                centerY = value.origin().getY() + 1.5 + hover;
                renderedRotation = -Math.round((float)(time * 0.35));
            }
            var distance = PortalTargetingMath.rayDistance(
                cameraPosition.x, cameraPosition.y, cameraPosition.z,
                look.x(), look.y(), look.z(),
                value.origin().getX() + 0.5, centerY, value.origin().getZ() + 0.5,
                renderedRotation, width, height, depth, maximumDistance);
            if (distance.isPresent() && distance.getAsDouble() < closestDistance) {
                closestDistance = distance.getAsDouble();
                closest = new PortalTooltipTarget(
                    Kind.LOADING, value.dungeonId(), value, value.origin(),
                    pointOnRay(cameraPosition, look, closestDistance));
            }
        }
        return Optional.ofNullable(closest);
    }

    private static Optional<PortalTooltipTarget> targetedPortal(Minecraft minecraft) {
        var camera = minecraft.gameRenderer.getMainCamera();
        var cameraPosition = camera.getPosition();
        Vector3f look = camera.getLookVector();
        double maximumDistance = ServerConfig.INSTANCE.portalHudDistance.get();
        PortalTooltipTarget closest = null;
        double closestDistance = maximumDistance + 1.0;
        for (ClientManifestation value : ClientManifestationStore.values()) {
            if (!value.dimension().equals(minecraft.level.dimension().location())) continue;
            if (value.state() != ManifestationState.PORTAL_OPEN
                && value.state() != ManifestationState.PORTAL_OPENING
                && value.state() != ManifestationState.CLOSING) continue;
            var distance = PortalTargetingMath.rayDistance(
                cameraPosition.x, cameraPosition.y, cameraPosition.z,
                look.x(), look.y(), look.z(),
                value.origin().getX() + 0.5, value.origin().getY() + 1.5, value.origin().getZ() + 0.5,
                value.rotationDegrees(),
                value.portalWidth() + PORTAL_TARGET_PADDING * 2.0F,
                value.portalHeight() + PORTAL_TARGET_PADDING * 2.0F,
                value.portalDepth() + PORTAL_TARGET_PADDING * 2.0F,
                maximumDistance);
            if (distance.isPresent() && distance.getAsDouble() < closestDistance) {
                closestDistance = distance.getAsDouble();
                closest = new PortalTooltipTarget(
                    Kind.PORTAL, value.dungeonId(), value, value.origin(),
                    pointOnRay(cameraPosition, look, closestDistance));
            }
        }
        for (ManifestationPortalBlockEntity portal : ManifestationPortalBlockEntity.loadedIn(minecraft.level)) {
            var distance = PortalTargetingMath.rayDistance(
                cameraPosition.x, cameraPosition.y, cameraPosition.z,
                look.x(), look.y(), look.z(),
                portal.getBlockPos().getX() + 0.5, portal.getBlockPos().getY() + 1.5, portal.getBlockPos().getZ() + 0.5,
                portal.rotationDegrees(),
                portal.portalWidth() + PORTAL_TARGET_PADDING * 2.0F,
                portal.portalHeight() + PORTAL_TARGET_PADDING * 2.0F,
                portal.portalDepth() + PORTAL_TARGET_PADDING * 2.0F,
                maximumDistance);
            if (distance.isEmpty() || distance.getAsDouble() >= closestDistance) continue;
            ClientManifestation manifestation = portal.manifestationId()
                .flatMap(ClientManifestationStore::find)
                .orElse(null);
            ResourceLocation dungeonId = manifestation != null
                ? manifestation.dungeonId()
                : portal.dungeonId().orElse(null);
            if (dungeonId != null) {
                closestDistance = distance.getAsDouble();
                closest = new PortalTooltipTarget(
                    Kind.PORTAL, dungeonId, manifestation, portal.getBlockPos(),
                    pointOnRay(cameraPosition, look, closestDistance));
            }
        }
        return Optional.ofNullable(closest);
    }

    private static Vec3 pointOnRay(Vec3 origin, Vector3f look, double distance) {
        return origin.add(look.x() * distance, look.y() * distance, look.z() * distance);
    }

    private static String countdownText(ClientManifestation value) {
        if (value == null || !value.portalCountdownActive()) return "Closes after exit";
        int ticks = value.portalCountdownRemainingTicks();
        if (ticks <= 0 || value.state() == ManifestationState.CLOSING) return "Closing";
        int seconds = (ticks + 19) / 20;
        return String.format("Closes in %d:%02d", seconds / 60, seconds % 60);
    }

    public enum Kind {
        LOADING,
        PORTAL
    }
}
