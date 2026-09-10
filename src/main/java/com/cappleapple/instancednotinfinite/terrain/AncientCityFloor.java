package com.cappleapple.instancednotinfinite.terrain;

/** Cavern floor height relative to the measured underside of the city's foundation. */
public final class AncientCityFloor {
    private AncientCityFloor() {
    }

    public static int height(long seed, int foundationSupportY, int x, int z, double distanceFromCity) {
        // City templates leave existing terrain beneath their foundations. Noise must not
        // lower that supporting plane, including the first six blocks of the entrance apron.
        double variation = Math.clamp((distanceFromCity - 6.0) / 8.0, 0.0, 1.0);
        if (variation == 0.0) return foundationSupportY;
        return foundationSupportY - (int) Math.floor(variation * 1.5
            * (1.0 + DeterministicNoise.smooth2d(seed ^ 0xF100, x, z, 24)));
    }
}
