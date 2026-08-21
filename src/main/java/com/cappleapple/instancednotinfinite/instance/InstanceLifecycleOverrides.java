package com.cappleapple.instancednotinfinite.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/** Optional catalyst/API/command overrides resolved once when an instance is created. */
public record InstanceLifecycleOverrides(
    Optional<Integer> openSeconds,
    Optional<Integer> postVisitSeconds,
    Optional<Integer> forceCollapseSeconds
) {
    public static final Codec<InstanceLifecycleOverrides> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("open_seconds").forGetter(InstanceLifecycleOverrides::openSeconds),
        Codec.INT.optionalFieldOf("post_visit_seconds").forGetter(InstanceLifecycleOverrides::postVisitSeconds),
        Codec.INT.optionalFieldOf("force_collapse_seconds").forGetter(InstanceLifecycleOverrides::forceCollapseSeconds)
    ).apply(instance, InstanceLifecycleOverrides::new));

    public InstanceLifecycleOverrides {
        openSeconds = normalize(openSeconds, "open_seconds");
        postVisitSeconds = normalize(postVisitSeconds, "post_visit_seconds");
        forceCollapseSeconds = normalize(forceCollapseSeconds, "force_collapse_seconds");
    }

    public static InstanceLifecycleOverrides empty() {
        return new InstanceLifecycleOverrides(Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static InstanceLifecycleOverrides of(int openSeconds, int postVisitSeconds, int forceCollapseSeconds) {
        return new InstanceLifecycleOverrides(
            Optional.of(openSeconds), Optional.of(postVisitSeconds), Optional.of(forceCollapseSeconds));
    }

    public boolean isEmpty() {
        return openSeconds.isEmpty() && postVisitSeconds.isEmpty() && forceCollapseSeconds.isEmpty();
    }

    public InstanceLifecycleSettings resolve(InstanceLifecycleSettings fallback) {
        return new InstanceLifecycleSettings(
            openSeconds.orElse(fallback.openSeconds()),
            postVisitSeconds.orElse(fallback.postVisitSeconds()),
            forceCollapseSeconds.orElse(fallback.forceCollapseSeconds()));
    }

    private static Optional<Integer> normalize(Optional<Integer> value, String name) {
        Optional<Integer> normalized = value == null ? Optional.empty() : value;
        normalized.ifPresent(seconds -> {
            if (seconds < InstanceLifecycleSettings.INFINITE || seconds > InstanceLifecycleSettings.MAX_SECONDS) {
                throw new IllegalArgumentException(
                    name + " must be -1 or between 0 and " + InstanceLifecycleSettings.MAX_SECONDS);
            }
        });
        return normalized;
    }
}
