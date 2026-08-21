package com.cappleapple.instancednotinfinite.client;

/** Presentation helpers for the animation-driven manifestation loading panel. */
final class LoadingProgressMath {
    private LoadingProgressMath() {
    }

    static int percentage(float animationProgress) {
        return Math.round(clamp(animationProgress) * 100.0F);
    }

    static int filledWidth(float animationProgress, int availableWidth) {
        return Math.round(clamp(animationProgress) * Math.max(0, availableWidth));
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
