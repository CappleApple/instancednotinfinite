package com.cappleapple.instancednotinfinite.recipe;

public final class StructureSizeEstimator {
    private StructureSizeEstimator() {
    }

    public static double fromVolume(long maximumTemplateVolume, int jigsawRadius, int jigsawDepth) {
        double volumeScore = maximumTemplateVolume <= 0 ? 0.0D
            : Math.max(0.0D, Math.min(1.0D, Math.log10(maximumTemplateVolume + 1.0D) / 6.0D));
        double radiusScore = Math.max(0.0D, Math.min(1.0D, jigsawRadius / 128.0D));
        double depthScore = Math.max(0.0D, Math.min(1.0D, jigsawDepth / 20.0D));
        return Math.max(volumeScore, radiusScore * 0.75D + depthScore * 0.25D);
    }
}
