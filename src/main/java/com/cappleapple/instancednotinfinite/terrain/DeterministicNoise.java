package com.cappleapple.instancednotinfinite.terrain;

public final class DeterministicNoise {
    private DeterministicNoise() {
    }

    public static double sample2d(long seed, int x, int z) {
        return unit(mix(seed ^ (long)x * 0x632BE59BD9B4E019L ^ (long)z * 0x9E3779B97F4A7C15L));
    }

    public static double sample3d(long seed, int x, int y, int z) {
        long mixed = seed ^ (long)x * 0x632BE59BD9B4E019L;
        mixed ^= (long)y * 0xC6BC279692B5CC83L;
        mixed ^= (long)z * 0x9E3779B97F4A7C15L;
        return unit(mix(mixed));
    }

    /** Smooth value noise sampled in block coordinates at a configurable lattice scale. */
    public static double smooth2d(long seed, int x, int z, int scale) {
        if (scale < 1) throw new IllegalArgumentException("scale must be positive");
        int gridX = Math.floorDiv(x, scale);
        int gridZ = Math.floorDiv(z, scale);
        double tx = smooth(Math.floorMod(x, scale) / (double)scale);
        double tz = smooth(Math.floorMod(z, scale) / (double)scale);
        double near = lerp(sample2d(seed, gridX, gridZ), sample2d(seed, gridX + 1, gridZ), tx);
        double far = lerp(sample2d(seed, gridX, gridZ + 1), sample2d(seed, gridX + 1, gridZ + 1), tx);
        return lerp(near, far, tz);
    }

    /** Smooth three-dimensional value noise for finite shell and cave shaping. */
    public static double smooth3d(long seed, int x, int y, int z, int scale) {
        if (scale < 1) throw new IllegalArgumentException("scale must be positive");
        int gridX = Math.floorDiv(x, scale);
        int gridY = Math.floorDiv(y, scale);
        int gridZ = Math.floorDiv(z, scale);
        double tx = smooth(Math.floorMod(x, scale) / (double)scale);
        double ty = smooth(Math.floorMod(y, scale) / (double)scale);
        double tz = smooth(Math.floorMod(z, scale) / (double)scale);
        double z0y0 = lerp(sample3d(seed, gridX, gridY, gridZ), sample3d(seed, gridX + 1, gridY, gridZ), tx);
        double z0y1 = lerp(sample3d(seed, gridX, gridY + 1, gridZ), sample3d(seed, gridX + 1, gridY + 1, gridZ), tx);
        double z1y0 = lerp(sample3d(seed, gridX, gridY, gridZ + 1), sample3d(seed, gridX + 1, gridY, gridZ + 1), tx);
        double z1y1 = lerp(sample3d(seed, gridX, gridY + 1, gridZ + 1), sample3d(seed, gridX + 1, gridY + 1, gridZ + 1), tx);
        return lerp(lerp(z0y0, z0y1, ty), lerp(z1y0, z1y1, ty), tz);
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static double unit(long value) {
        return ((value >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private static double smooth(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double first, double second, double amount) {
        return first + (second - first) * amount;
    }
}
