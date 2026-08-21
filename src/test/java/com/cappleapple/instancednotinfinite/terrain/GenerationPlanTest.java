package com.cappleapple.instancednotinfinite.terrain;

import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenerationPlanTest {
    @Test
    void lowersGroundThatWouldCoverAWholeNetherStructure() {
        assertEquals(48, TerrainSurfaceSeating.seat(EnvironmentType.NETHER_LIKE, 49, 155, 158));
    }

    @Test
    void seatsGroundBelowDominantFoundationInsteadOfHangingLava() {
        assertEquals(60, TerrainSurfaceSeating.seat(EnvironmentType.NETHER_LIKE, 49, 155, 61, 158));
    }

    @Test
    void terrainAdaptedFoundationCanRemainBuriedToItsAuthoredTop() {
        assertEquals(70, TerrainSurfaceSeating.seat(EnvironmentType.NETHER_LIKE, 49, 155, 71, 158));
    }

    @Test
    void preservesAuthoredSurfacePassingThroughAFoundation() {
        assertEquals(127, TerrainSurfaceSeating.seat(EnvironmentType.SURFACE, 114, 226, 127));
        assertEquals(127, TerrainSurfaceSeating.seat(EnvironmentType.SURFACE, 90, 127, 127));
        assertEquals(127, TerrainSurfaceSeating.seat(EnvironmentType.SURFACE, 90, 120, 127));
    }

    @Test
    void oceanWaterlineIsNotTreatedAsBuryingGround() {
        assertEquals(175, TerrainSurfaceSeating.seat(EnvironmentType.OCEAN_SURFACE, 172, 209, 175));
    }
}
