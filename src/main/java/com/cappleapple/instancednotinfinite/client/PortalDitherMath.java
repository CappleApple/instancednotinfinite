package com.cappleapple.instancednotinfinite.client;

/** Timer-driven size and dissolve curves for portal geometry and its edge fragments. */
final class PortalDitherMath {
    private static final float FRAGMENT_SPAWN_FRACTION = 0.18F;

    private PortalDitherMath() {
    }

    static float portalSizeScale(float remainingFraction, float minimumScale) {
        float minimum = clamp(minimumScale);
        return minimum + (1.0F - minimum) * (float)Math.sqrt(clamp(remainingFraction));
    }

    static float fixedBottomCenterOffset(float fullHalfHeight, float sizeScale) {
        return fullHalfHeight * (clamp(sizeScale) - 1.0F);
    }

    static float fragmentScale(float age, float collapseProgress) {
        float life = 1.0F - fragmentAnimationAge(age);
        return life * life * (float)Math.sqrt(1.0F - clamp(collapseProgress));
    }

    static float fragmentSpawnFade(float age) {
        float progress = clamp(age / FRAGMENT_SPAWN_FRACTION);
        return progress * progress * (3.0F - 2.0F * progress);
    }

    static float fragmentAnimationAge(float age) {
        return clamp((clamp(age) - FRAGMENT_SPAWN_FRACTION) / (1.0F - FRAGMENT_SPAWN_FRACTION));
    }

    static float fragmentSpawnInset(float age, float maximumInset) {
        return Math.max(0.0F, maximumInset) * (1.0F - fragmentSpawnFade(age));
    }

    static float fragmentAlpha(float age, float collapseProgress) {
        return fragmentSpawnFade(age)
            * (1.0F - fragmentAnimationAge(age))
            * (1.0F - clamp(collapseProgress));
    }

    static float dissolveStart(int column, int row, int columns, int rows) {
        int horizontalEdgeDistance = Math.min(column, columns - 1 - column);
        int verticalEdgeDistance = Math.min(row, rows - 1 - row);
        int edgeDistance = Math.min(horizontalEdgeDistance, verticalEdgeDistance);
        float maximumEdgeDistance = Math.max(1.0F, Math.min(columns, rows) * 0.5F);
        float centerBias = Math.min(1.0F, edgeDistance / maximumEdgeDistance);
        float random = noise(column * 197 + row * 389, 43);
        return 0.04F + 0.92F * (0.58F * random + 0.42F * centerBias);
    }

    static float disperseProgress(float collapseProgress, float dissolveStart) {
        float progress = clamp(collapseProgress);
        float start = clamp(dissolveStart);
        if (progress <= start) return 0.0F;
        if (start >= 1.0F) return progress >= 1.0F ? 1.0F : 0.0F;
        return clamp((progress - start) / (1.0F - start));
    }

    static float disperseScale(float disperseProgress) {
        float remaining = 1.0F - clamp(disperseProgress);
        return remaining * remaining;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float noise(int value, int salt) {
        int mixed = value * 0x45D9F3B + salt * 0x119DE1F3;
        mixed = (mixed ^ (mixed >>> 16)) * 0x45D9F3B;
        mixed ^= mixed >>> 16;
        return (mixed & 0x00FF_FFFF) / (float)0x0100_0000;
    }
}
