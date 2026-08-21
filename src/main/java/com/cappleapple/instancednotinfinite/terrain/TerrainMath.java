package com.cappleapple.instancednotinfinite.terrain;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

final class TerrainMath {
    private static final long BROAD_SURFACE_SALT = 0xA52F19D4C3B2E187L;
    private static final long FINE_SURFACE_SALT = 0x1F83D9ABFB41BD6BL;

    private TerrainMath() {
    }

    static double boxDistance2d(BoundingBox guaranteed, BoundingBox outer, int x, int z) {
        return RectangularFalloff.distance2d(
            x, z,
            guaranteed.minX(), guaranteed.maxX(), guaranteed.minZ(), guaranteed.maxZ(),
            outer.minX(), outer.maxX(), outer.minZ(), outer.maxZ());
    }

    static double boxDistance3d(BoundingBox guaranteed, BoundingBox outer, int x, int y, int z) {
        return RectangularFalloff.distance3d(
            x, y, z,
            guaranteed.minX(), guaranteed.maxX(), guaranteed.minY(), guaranteed.maxY(), guaranteed.minZ(), guaranteed.maxZ(),
            outer.minX(), outer.maxX(), outer.minY(), outer.maxY(), outer.minZ(), outer.maxZ());
    }

    static int surfaceVariation(long seed, int x, int z) {
        double broad = DeterministicNoise.smooth2d(seed ^ BROAD_SURFACE_SALT, x, z, 17) * 3.5;
        double fine = DeterministicNoise.smooth2d(seed ^ FINE_SURFACE_SALT, x, z, 7) * 1.25;
        return (int)Math.round(broad + fine);
    }

    static double smoothUnit(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }
}
