package com.cappleapple.instancednotinfinite.instance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizontalRadiusGuardTest {
    @Test
    void ignoresVerticalPositionByDesign() {
        assertFalse(HorizontalRadiusGuard.isOutside(0, 0, 124));
    }

    @Test
    void acceptsPointsOnTheHorizontalBoundary() {
        assertFalse(HorizontalRadiusGuard.isOutside(124, 0, 124));
        assertFalse(HorizontalRadiusGuard.isOutside(87, 87, 124));
    }

    @Test
    void rejectsPointsBeyondTheHorizontalBoundary() {
        assertTrue(HorizontalRadiusGuard.isOutside(125, 0, 124));
        assertTrue(HorizontalRadiusGuard.isOutside(88, 88, 124));
    }

    @Test
    void rejectsNegativeRadii() {
        assertThrows(IllegalArgumentException.class, () -> HorizontalRadiusGuard.isOutside(0, 0, -1));
    }
}
