package com.cappleapple.instancednotinfinite.api;

import com.cappleapple.instancednotinfinite.manifestation.AnimationMode;
import com.cappleapple.instancednotinfinite.manifestation.DungeonManifestation;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationState;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public record ManifestationView(
    UUID id,
    ResourceLocation originDimension,
    BlockPos origin,
    int rotationDegrees,
    UUID instanceId,
    ResourceLocation dungeonId,
    AnimationMode animationMode,
    ManifestationState state,
    double generationProgress,
    double animationProgress,
    Optional<String> failureReason
) {
    public static ManifestationView from(DungeonManifestation value) {
        return new ManifestationView(
            value.id(), value.originDimension(), value.origin(), value.rotationDegrees(), value.instanceId().value(),
            value.dungeonId(), value.animationMode(), value.state(), value.generationProgress(),
            value.animationProgress(), value.failureReason());
    }

    public Direction orientation() {
        return com.cappleapple.instancednotinfinite.manifestation.PortalRotation.nearestDirection(rotationDegrees);
    }
}
