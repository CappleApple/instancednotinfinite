package com.cappleapple.instancednotinfinite.terrain;

/** Carries the sampled waterline with a vertically fitted structure, independent of its roof. */
public final class OceanSurfaceWaterline {
    private OceanSurfaceWaterline() {
    }

    public static int translate(int sampledWaterline, int originalMinimumY, int fittedMinimumY) {
        return sampledWaterline + fittedMinimumY - originalMinimumY;
    }
}
