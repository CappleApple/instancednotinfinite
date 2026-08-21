package com.cappleapple.instancednotinfinite.terrain;

final class RectangularFalloff {
    private RectangularFalloff() {
    }

    static double distance2d(
        int x,
        int z,
        int guaranteedMinX,
        int guaranteedMaxX,
        int guaranteedMinZ,
        int guaranteedMaxZ,
        int outerMinX,
        int outerMaxX,
        int outerMinZ,
        int outerMaxZ
    ) {
        double dx = axisDistance(x, guaranteedMinX, guaranteedMaxX, outerMinX, outerMaxX);
        double dz = axisDistance(z, guaranteedMinZ, guaranteedMaxZ, outerMinZ, outerMaxZ);
        return Math.sqrt(dx * dx + dz * dz);
    }

    static double distance3d(
        int x,
        int y,
        int z,
        int guaranteedMinX,
        int guaranteedMaxX,
        int guaranteedMinY,
        int guaranteedMaxY,
        int guaranteedMinZ,
        int guaranteedMaxZ,
        int outerMinX,
        int outerMaxX,
        int outerMinY,
        int outerMaxY,
        int outerMinZ,
        int outerMaxZ
    ) {
        double dx = axisDistance(x, guaranteedMinX, guaranteedMaxX, outerMinX, outerMaxX);
        double dy = axisDistance(y, guaranteedMinY, guaranteedMaxY, outerMinY, outerMaxY);
        double dz = axisDistance(z, guaranteedMinZ, guaranteedMaxZ, outerMinZ, outerMaxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double axisDistance(int value, int guaranteedMin, int guaranteedMax, int outerMin, int outerMax) {
        if (value < guaranteedMin) {
            return (double)(guaranteedMin - value) / Math.max(1, guaranteedMin - outerMin);
        }
        if (value > guaranteedMax) {
            return (double)(value - guaranteedMax) / Math.max(1, outerMax - guaranteedMax);
        }
        return 0.0;
    }
}
