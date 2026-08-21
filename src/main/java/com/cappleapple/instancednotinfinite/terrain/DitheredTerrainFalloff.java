package com.cappleapple.instancednotinfinite.terrain;

/** Deterministic coverage masks that dissolve the finite terrain shell into void. */
final class DitheredTerrainFalloff {
    private static final long COARSE_SALT = 0x6A09E667F3BCC909L;
    private static final long FINE_SALT = 0xBB67AE8584CAA73BL;
    private static final long DITHER_SALT = 0x3C6EF372FE94F82BL;

    private DitheredTerrainFalloff() {
    }

    static boolean includesSurfaceVoxel(GenerationPlan plan, int x, int y, int z) {
        double distance = TerrainMath.boxDistance2d(plan.guaranteedBounds(), plan.envelopeBounds(), x, z);
        if (distance <= 0.0) return true;
        if (distance >= 1.0) return false;
        double warp = DeterministicNoise.smooth3d(plan.seed() ^ COARSE_SALT, x, y, z, 11) * 0.13
            + DeterministicNoise.smooth3d(plan.seed() ^ FINE_SALT, x, y, z, 5) * 0.045;
        double coverage = coverage(distance, warp);
        double dither = (DeterministicNoise.sample3d(plan.seed() ^ DITHER_SALT, x, y, z) + 1.0) * 0.5;
        return dither < coverage;
    }

    static boolean includesVoxel(GenerationPlan plan, int x, int y, int z) {
        double distance = TerrainMath.boxDistance3d(plan.guaranteedBounds(), plan.envelopeBounds(), x, y, z);
        if (distance <= 0.0) return true;
        if (distance >= 1.0) return false;
        double warp = DeterministicNoise.smooth3d(plan.seed() ^ COARSE_SALT, x, y, z, 11) * 0.12
            + DeterministicNoise.smooth3d(plan.seed() ^ FINE_SALT, x, y, z, 5) * 0.04;
        double coverage = coverage(distance, warp);
        double dither = (DeterministicNoise.sample3d(plan.seed() ^ DITHER_SALT, x, y, z) + 1.0) * 0.5;
        return dither < coverage;
    }

    static double coverage(double distance, double warp) {
        if (distance <= 0.0) return 1.0;
        if (distance >= 1.0) return 0.0;
        // Warp disappears at both ends so the guaranteed area remains whole and the
        // outermost envelope always becomes void instead of touching a hard cutoff.
        double warpWeight = 4.0 * distance * (1.0 - distance);
        double warpedDistance = clamp(distance + warp * warpWeight, 0.0, 1.0);
        double faded = smoothstep(0.08, 1.0, warpedDistance);
        return 1.0 - faded;
    }

    private static double smoothstep(double start, double end, double value) {
        double normalized = clamp((value - start) / (end - start), 0.0, 1.0);
        return normalized * normalized * (3.0 - 2.0 * normalized);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
