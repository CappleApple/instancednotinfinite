package com.cappleapple.instancednotinfinite.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DitheredTerrainFalloffTest {
    @Test
    void coverageKeepsTheGuaranteedAreaAndReachesVoidAtTheEnvelope() {
        assertEquals(1.0, DitheredTerrainFalloff.coverage(0.0, 1.0));
        assertEquals(0.0, DitheredTerrainFalloff.coverage(1.0, -1.0));
    }

    @Test
    void coverageFormsAProgressiveGradient() {
        double inner = DitheredTerrainFalloff.coverage(0.25, 0.0);
        double middle = DitheredTerrainFalloff.coverage(0.50, 0.0);
        double outer = DitheredTerrainFalloff.coverage(0.75, 0.0);
        assertTrue(inner > middle);
        assertTrue(middle > outer);
        assertTrue(outer > 0.0);
    }
}
