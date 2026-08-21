package com.cappleapple.instancednotinfinite.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoundationSeatingReferenceTest {
    private static final DominantFoundationLevel.FoundationSpan FOUNDATION =
        new DominantFoundationLevel.FoundationSpan(61, 70);

    @Test
    void terrainAdaptationBuriesTheAuthoredFoundationPlateau() {
        assertEquals(71, FoundationSeatingReference.select(FOUNDATION, 62, true));
    }

    @Test
    void unadaptedStructuresRemainBelowTheirLowestFoundationBlock() {
        assertEquals(61, FoundationSeatingReference.select(FOUNDATION, 62, false));
    }

    @Test
    void explicitPlacementGroundCanRequireDeeperBurial() {
        assertEquals(76, FoundationSeatingReference.select(FOUNDATION, 76, true));
    }
}
