package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.manifestation.ManifestationState;
import com.cappleapple.instancednotinfinite.manifestation.PreparationParticleStyle;
import java.util.Random;
import com.cappleapple.instancednotinfinite.compat.SableCoordinates;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

/** Immediate visual cue while the server prepares the first structure snapshot batch. */
final class ManifestationPreparationParticles {
    private static final double TAU = Math.PI * 2.0;

    private ManifestationPreparationParticles() {
    }

    static void tick(ClientManifestation value) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || value.blockCount() > 0 || value.preparationParticleRate() <= 0
            || value.preparationParticleStyle() == PreparationParticleStyle.NONE
            || (value.state() != ManifestationState.PREPARING
                && value.state() != ManifestationState.GENERATING
                && value.state() != ManifestationState.MANIFESTING)) {
            return;
        }

        if (!SableCoordinates.available(level, value.origin())) return;
        var subLevel = SableCoordinates.subLevel(level, value.origin());
        Pose3dc pose = subLevel == null ? null : subLevel.logicalPose();
        int rgb = value.preparationParticleColor();
        DustParticleOptions particle = new DustParticleOptions(new Vector3f(
            (rgb >> 16 & 0xFF) / 255.0F,
            (rgb >> 8 & 0xFF) / 255.0F,
            (rgb & 0xFF) / 255.0F), value.preparationParticleScale());
        double centerX = value.origin().getX() + 0.5;
        double centerZ = value.origin().getZ() + 0.5;
        double height = Math.max(value.maximumHeight(), value.portalHeight());
        double centerY = value.origin().getY() + height * 0.5;
        double radius = value.preparationParticleRadius();
        double gameTime = ClientAnimationTime.ticks();
        Random random = new Random(value.id().getMostSignificantBits() ^ value.id().getLeastSignificantBits() ^ (long)gameTime);

        for (int index = 0; index < value.preparationParticleRate(); index++) {
            double fraction = index / (double)Math.max(1, value.preparationParticleRate());
            switch (value.preparationParticleStyle()) {
                case RING -> ring(level, pose, particle, centerX, centerY, centerZ, radius, gameTime, fraction);
                case SPIRAL -> spiral(level, pose, particle, centerX, value.origin().getY(), centerZ, radius, height, gameTime, fraction);
                case CONVERGING -> converging(level, pose, particle, centerX, centerY, centerZ, radius, height, random);
                case NONE -> {
                }
            }
        }
    }

    private static void ring(
        ClientLevel level,
        Pose3dc pose,
        DustParticleOptions particle,
        double centerX,
        double centerY,
        double centerZ,
        double radius,
        double gameTime,
        double fraction
    ) {
        double angle = gameTime * 0.16 + fraction * TAU;
        double y = centerY + Math.sin(gameTime * 0.08 + fraction * TAU) * 0.2;
        addParticle(level, pose, particle, centerX + Math.cos(angle) * radius, y, centerZ + Math.sin(angle) * radius,
            0.0, 0.012, 0.0);
    }

    private static void spiral(
        ClientLevel level,
        Pose3dc pose,
        DustParticleOptions particle,
        double centerX,
        double baseY,
        double centerZ,
        double radius,
        double height,
        double gameTime,
        double fraction
    ) {
        double phase = gameTime * 0.18 + fraction * TAU;
        double yProgress = (gameTime * 0.025 + fraction) % 1.0;
        double x = centerX + Math.cos(phase) * radius;
        double z = centerZ + Math.sin(phase) * radius;
        addParticle(level, pose, particle, x, baseY + yProgress * height, z,
            -Math.sin(phase) * 0.012, 0.015, Math.cos(phase) * 0.012);
    }

    private static void converging(
        ClientLevel level,
        Pose3dc pose,
        DustParticleOptions particle,
        double centerX,
        double centerY,
        double centerZ,
        double radius,
        double height,
        Random random
    ) {
        double angle = random.nextDouble() * TAU;
        double x = centerX + Math.cos(angle) * radius;
        double y = centerY + (random.nextDouble() - 0.5) * height;
        double z = centerZ + Math.sin(angle) * radius;
        addParticle(level, pose, particle, x, y, z,
            (centerX - x) * 0.025, (centerY - y) * 0.025, (centerZ - z) * 0.025);
    }

    private static void addParticle(ClientLevel level, Pose3dc pose, DustParticleOptions particle,
            double x, double y, double z, double vx, double vy, double vz) {
        Vec3 position = new Vec3(x, y, z);
        Vec3 velocity = new Vec3(vx, vy, vz);
        if (pose != null) {
            position = pose.transformPosition(position);
            velocity = pose.transformNormal(velocity);
        }
        level.addParticle(particle, position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
    }
}
