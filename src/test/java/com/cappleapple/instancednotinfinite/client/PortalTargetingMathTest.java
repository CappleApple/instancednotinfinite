package com.cappleapple.instancednotinfinite.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PortalTargetingMathTest {
    @Test
    void intersectsPortalFromEitherSide() {
        assertEquals(4.9375, PortalTargetingMath.rayDistance(
            0, 0, 5, 0, 0, -1, 0, 0, 0, 0, 2, 3, 0, 16).orElseThrow(), 1.0E-9);
        assertEquals(4.9375, PortalTargetingMath.rayDistance(
            0, 0, -5, 0, 0, 1, 0, 0, 0, 0, 2, 3, 0, 16).orElseThrow(), 1.0E-9);
    }

    @Test
    void followsIntegerDiagonalRotation() {
        double camera = 5.0 / Math.sqrt(2.0);
        assertTrue(PortalTargetingMath.rayDistance(
            -camera, 0, camera, 1 / Math.sqrt(2.0), 0, -1 / Math.sqrt(2.0),
            0, 0, 0, 45, 2, 3, 0, 16).isPresent());
    }

    @Test
    void rejectsHitsBeyondConfiguredDistance() {
        assertTrue(PortalTargetingMath.rayDistance(
            0, 0, 5, 0, 0, -1, 0, 0, 0, 0, 2, 3, 0, 4).isEmpty());
    }
}
