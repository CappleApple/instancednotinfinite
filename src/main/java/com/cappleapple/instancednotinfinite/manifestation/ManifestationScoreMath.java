package com.cappleapple.instancednotinfinite.manifestation;

import java.util.SplittableRandom;

/** Minecraft-free deterministic score implementation. */
public final class ManifestationScoreMath {
    private ManifestationScoreMath() {
    }

    public static double score(
        int x, int y, int z,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        AnimationMode mode,
        long seed
    ) {
        double nx = normalized(x, minX, maxX);
        double ny = normalized(y, minY, maxY);
        double nz = normalized(z, minZ, maxZ);
        double centerDistance = Math.sqrt(sq(nx - 0.5) + sq(ny - 0.5) + sq(nz - 0.5)) / Math.sqrt(0.75);
        double edgeDistance = Math.min(Math.min(Math.min(nx, 1.0 - nx), Math.min(ny, 1.0 - ny)), Math.min(nz, 1.0 - nz));
        double noise2 = valueNoise3(x, 0.0, z, seed, 12.0);
        double noise3 = valueNoise3(x, y, z, seed, 9.0);
        return clamp01(switch (mode) {
            case GROUND_UP -> ny + (noise2 - 0.5) * 0.34;
            case MIDDLE_OUT -> centerDistance * 0.88 + noise3 * 0.12;
            case OUTSIDE_IN -> edgeDistance * 1.8 + noise3 * 0.1;
            case SINGLE_ORIGIN -> distanceToOrigins(nx, ny, nz, seed, 1) * 0.9 + noise3 * 0.1;
            case MULTI_ORIGIN -> distanceToOrigins(nx, ny, nz, seed, 5) * 0.9 + noise3 * 0.1;
            case CHAOTIC -> valueNoise3(x, y, z, seed ^ 0x63A9D47EL, 5.0) * 0.75 + noise3 * 0.25;
            case RANDOM_ORDER -> (hash01(x, y, z, seed) * 0.7) + (noise3 * 0.3);
            case NONE -> 0.0;
            case RANDOM_MODE -> throw new IllegalArgumentException("RANDOM_MODE must be resolved before scoring");
        });
    }

    /** Makes the first captured structure score the start of the visible reveal. */
    public static double normalizeRevealScore(double score, double firstBatchMinimum) {
        double floor = clamp01(firstBatchMinimum);
        if (floor >= 1.0) return 0.0;
        return clamp01((clamp01(score) - floor) / (1.0 - floor));
    }

    private static double distanceToOrigins(double x, double y, double z, long seed, int count) {
        SplittableRandom random = new SplittableRandom(seed ^ 0xD16E0A5B9L);
        double nearest = Double.MAX_VALUE;
        for (int index = 0; index < count; index++) {
            double ox = 0.1 + random.nextDouble() * 0.8;
            double oy = 0.1 + random.nextDouble() * 0.8;
            double oz = 0.1 + random.nextDouble() * 0.8;
            nearest = Math.min(nearest, Math.sqrt(sq(x - ox) + sq(y - oy) + sq(z - oz)) / Math.sqrt(3.0));
        }
        return nearest;
    }

    private static double valueNoise3(double x, double y, double z, long seed, double wavelength) {
        double sx = x / wavelength;
        double sy = y / wavelength;
        double sz = z / wavelength;
        int x0 = floor(sx);
        int y0 = floor(sy);
        int z0 = floor(sz);
        double tx = smooth(sx - x0);
        double ty = smooth(sy - y0);
        double tz = smooth(sz - z0);
        double c00 = lerp(hash01(x0, y0, z0, seed), hash01(x0 + 1, y0, z0, seed), tx);
        double c01 = lerp(hash01(x0, y0, z0 + 1, seed), hash01(x0 + 1, y0, z0 + 1, seed), tx);
        double c10 = lerp(hash01(x0, y0 + 1, z0, seed), hash01(x0 + 1, y0 + 1, z0, seed), tx);
        double c11 = lerp(hash01(x0, y0 + 1, z0 + 1, seed), hash01(x0 + 1, y0 + 1, z0 + 1, seed), tx);
        return lerp(lerp(c00, c01, tz), lerp(c10, c11, tz), ty);
    }

    private static int floor(double value) {
        int integer = (int)value;
        return value < integer ? integer - 1 : integer;
    }

    private static double hash01(int x, int y, int z, long seed) {
        long value = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double normalized(int value, int min, int max) { return max == min ? 0.5 : (value - min) / (double)(max - min); }
    private static double smooth(double value) { return value * value * (3.0 - 2.0 * value); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double sq(double value) { return value * value; }
    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
