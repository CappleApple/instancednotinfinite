package com.cappleapple.instancednotinfinite.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AncientCityFloorTest {
    private static final long[] SEEDS = {73L, 7812260311881519708L, 5251186036548626152L, -3399384581734563066L};

    @Test
    void foundationAndEntranceApronHaveContinuousSupportAcrossLayouts() {
        for (long seed : SEEDS) {
            for (int x = -128; x <= 128; x++) {
                for (int z = -128; z <= 128; z++) {
                    assertEquals(-3, AncientCityFloor.height(seed, -3, x, z, 0.0),
                        "Terrain dropped below the authored foundation");
                    assertEquals(-3, AncientCityFloor.height(seed, -3, x, z, 6.0),
                        "Entrance apron dropped below the supported city floor");
                }
            }
        }
    }

    @Test
    void outskirtsKeepDeterministicVariationWithoutRisingIntoTheCity() {
        int lowered = 0;
        for (int x = -128; x <= 128; x++) {
            int floor = AncientCityFloor.height(SEEDS[3], 89, x, 41, 18.0);
            assertEquals(floor, AncientCityFloor.height(SEEDS[3], 89, x, 41, 18.0));
            assertTrue(floor <= 89 && floor >= 86, "Outskirts exceed the allowed floor variation");
            if (floor < 89) lowered++;
        }
        assertTrue(lowered > 0, "Cavern outskirts lost their floor variation");
    }
}
