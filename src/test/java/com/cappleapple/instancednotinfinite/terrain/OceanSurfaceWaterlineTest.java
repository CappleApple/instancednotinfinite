package com.cappleapple.instancednotinfinite.terrain;

import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OceanSurfaceWaterlineTest {
    @Test
    void highAbsoluteOceanStructurePreservesSeaLevelThroughVerticalFit() {
        assertEquals(
            43,
            OceanSurfaceWaterline.translate(EnvironmentType.OCEAN_SURFACE, 127, 63, 200, 180, 200),
            "Acropolis must translate the authored Y=63 sea level, not the fallback Y=127 surface");
    }

    @Test
    void projectedTopLayerStructureRetainsGeneratedSurface() {
        assertEquals(
            107,
            OceanSurfaceWaterline.translate(EnvironmentType.OCEAN_SURFACE, 127, 63, 200, 180, null),
            "Malkuth-style top-layer structures still follow the generated surface");
    }

    @Test
    void nonOceanEnvironmentNeverUsesTheAuthoredOceanRule() {
        assertEquals(
            107,
            OceanSurfaceWaterline.translate(EnvironmentType.UNDERWATER, 127, 63, 200, 180, 200));
    }
}
