package com.cappleapple.instancednotinfinite.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RectangularFalloffTest {
    @Test
    void entirePaddedRectangleIsGuaranteed() {
        assertEquals(0.0, distance(100, 30));
        assertEquals(0.0, distance(-100, -30));
    }

    @Test
    void elongatedRegionDoesNotCollapseIntoLargestRadiusSphere() {
        assertEquals(0.0, distance(100, 0));
        assertTrue(distance(0, 75) > 1.0);
    }

    @Test
    void falloffIsNormalizedPerSide() {
        assertEquals(0.5, distance(110, 0), 0.000001);
        assertEquals(1.0, distance(120, 0), 0.000001);
        assertTrue(distance(120, 70) > 1.0);
    }

    private static double distance(int x, int z) {
        return RectangularFalloff.distance2d(x, z, -100, 100, -50, 50, -120, 120, -70, 70);
    }
}
