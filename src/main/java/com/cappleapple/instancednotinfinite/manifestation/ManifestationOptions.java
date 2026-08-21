package com.cappleapple.instancednotinfinite.manifestation;

import com.cappleapple.instancednotinfinite.instance.InstanceLifecycleOverrides;
import java.util.Objects;
import net.minecraft.core.Direction;

/** Immutable per-manifestation overrides. Server configuration supplies all omitted values. */
public record ManifestationOptions(
    int rotationDegrees,
    AnimationMode animationMode,
    InstanceLifecycleOverrides lifecycleOverrides
) {
    public ManifestationOptions {
        Objects.requireNonNull(animationMode, "animationMode");
        Objects.requireNonNull(lifecycleOverrides, "lifecycleOverrides");
        rotationDegrees = PortalRotation.normalize(rotationDegrees);
    }

    public ManifestationOptions(Direction orientation, AnimationMode animationMode) {
        this(PortalRotation.fromDirection(orientation), animationMode, InstanceLifecycleOverrides.empty());
    }

    public ManifestationOptions(int rotationDegrees, AnimationMode animationMode) {
        this(rotationDegrees, animationMode, InstanceLifecycleOverrides.empty());
    }

    public ManifestationOptions(Direction orientation, AnimationMode animationMode, InstanceLifecycleOverrides lifecycleOverrides) {
        this(PortalRotation.fromDirection(orientation), animationMode, lifecycleOverrides);
    }

    public Direction orientation() {
        return PortalRotation.nearestDirection(rotationDegrees);
    }

    public static ManifestationOptions defaults(Direction orientation) {
        return new ManifestationOptions(orientation, AnimationMode.RANDOM_MODE, InstanceLifecycleOverrides.empty());
    }

    public static ManifestationOptions defaults(int rotationDegrees) {
        return new ManifestationOptions(rotationDegrees, AnimationMode.RANDOM_MODE, InstanceLifecycleOverrides.empty());
    }
}
