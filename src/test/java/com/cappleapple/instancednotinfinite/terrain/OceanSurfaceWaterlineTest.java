package com.cappleapple.instancednotinfinite.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OceanSurfaceWaterlineTest {
    @Test
    void retainsTheSampledWaterlineRegardlessOfStructureHeight() {
        assertEquals(63, OceanSurfaceWaterline.translate(63, 58, 58));
        assertEquals(63, OceanSurfaceWaterline.translate(63, 20, 20));
        assertEquals(63, OceanSurfaceWaterline.translate(63, 200, 200));
    }

    @Test
    void waterlineMovesWithVerticalFitting() {
        assertEquals(43, OceanSurfaceWaterline.translate(63, 200, 180));
        assertEquals(83, OceanSurfaceWaterline.translate(63, -80, -60));
    }
}
