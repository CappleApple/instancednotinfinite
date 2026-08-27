package com.cappleapple.instancednotinfinite.definition;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacementEnvironmentInferenceTest {
    @Test
    void seaLevelAnchoredStructureIgnoresMisleadingSerializedStartHeight() {
        assertEquals(EnvironmentType.OCEAN_SURFACE, classify(58, 124, 58, false));
    }

    @Test
    void tallSeabedStructureDoesNotBecomeAShipJustBecauseItsRoofBreaksTheSurface() {
        assertEquals(EnvironmentType.UNDERWATER, classify(39, 100, 39, true));
        assertEquals(EnvironmentType.UNDERWATER, classify(39, 100, 39, false));
    }

    @Test
    void submergedAbsoluteStructureRemainsUnderwater() {
        assertEquals(EnvironmentType.UNDERWATER, classify(20, 55, 20, false));
    }

    @Test
    void skyPlacementIsDetectedOverLandAndWater() {
        assertEquals(EnvironmentType.FLOATING_ISLAND, classify(200, 250, 200, false));
        assertEquals(EnvironmentType.FLOATING_ISLAND, PlacementEnvironmentInference.classify(
            EnvironmentType.SURFACE, 140, 210, 140, 63, null, false));
    }

    @Test
    void tallGroundedBuildingIsNotFloating() {
        assertEquals(EnvironmentType.SURFACE, PlacementEnvironmentInference.classify(
            EnvironmentType.SURFACE, 63, 280, 63, 63, null, false));
    }

    @Test
    void undergroundAndCustomHandlersAreNotReclassified() {
        for (var environment : new EnvironmentType[]{EnvironmentType.CAVE, EnvironmentType.UNDERGROUND, EnvironmentType.CUSTOM}) {
            assertEquals(environment, PlacementEnvironmentInference.classify(environment, 200, 250, 200, 63, null, false));
        }
    }

    private static EnvironmentType classify(int minY, int maxY, int groundY, boolean floor) {
        return PlacementEnvironmentInference.classify(EnvironmentType.UNDERWATER, minY, maxY, groundY, 63, 39, floor);
    }
}
