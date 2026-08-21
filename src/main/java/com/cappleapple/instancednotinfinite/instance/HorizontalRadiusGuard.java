package com.cappleapple.instancednotinfinite.instance;

final class HorizontalRadiusGuard {
    private HorizontalRadiusGuard() {
    }

    static boolean isOutside(int x, int z, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Radius cannot be negative");
        }

        long distanceSquared = (long)x * x + (long)z * z;
        long radiusSquared = (long)radius * radius;
        return distanceSquared > radiusSquared;
    }
}
