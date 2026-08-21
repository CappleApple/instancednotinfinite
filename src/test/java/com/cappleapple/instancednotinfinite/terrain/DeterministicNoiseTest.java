package com.cappleapple.instancednotinfinite.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicNoiseTest {
    @Test
    void samplesAreStableAndBounded() {
        double sample = DeterministicNoise.sample3d(42L, 10, 20, 30);
        assertEquals(sample, DeterministicNoise.sample3d(42L, 10, 20, 30));
        assertTrue(sample >= -1.0 && sample <= 1.0);
    }

    @Test
    void coordinatesInfluenceSamples() {
        assertNotEquals(DeterministicNoise.sample2d(42L, 1, 2), DeterministicNoise.sample2d(42L, 2, 2));
    }

    @Test
    void smoothNoiseRetainsLatticeSamplesAndSupportsNegativeCoordinates() {
        assertEquals(
            DeterministicNoise.sample2d(91L, -2, 3),
            DeterministicNoise.smooth2d(91L, -16, 24, 8),
            0.0000001);
        double adjacent = DeterministicNoise.smooth3d(91L, -15, 7, 24, 8);
        assertTrue(adjacent >= -1.0 && adjacent <= 1.0);
        assertEquals(adjacent, DeterministicNoise.smooth3d(91L, -15, 7, 24, 8));
    }
}
