package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.manifestation.ManifestationState;

/** Anchors a server-confirmed phase once; subsequent world-time corrections cannot rewind it. */
final class AnimationPhaseClock {
    private ManifestationState state;
    private long serverStart;
    private double localStart;

    void sync(ManifestationState next, long changedAt, long observedGameTime, double localTime) {
        if (state == next && serverStart == changedAt) return;
        state = next;
        serverStart = changedAt;
        localStart = localTime - Math.max(0L, observedGameTime - changedAt);
    }

    float progress(double localTime, int durationTicks) {
        return (float)Math.clamp((localTime - localStart) / Math.max(1, durationTicks), 0.0, 1.0);
    }
}
