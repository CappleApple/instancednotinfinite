package com.cappleapple.instancednotinfinite.terrain;

import com.cappleapple.instancednotinfinite.definition.EnvironmentType;

/** Pure rule for carrying an ocean structure's intended waterline through vertical fitting. */
public final class OceanSurfaceWaterline {
    private OceanSurfaceWaterline() {
    }

    public static int translate(
        EnvironmentType environment,
        int generatedSurfaceY,
        int seaLevel,
        int originalStructureMinY,
        int fittedStructureMinY,
        Integer absoluteStartHeight
    ) {
        int sourceWaterline = preservesAuthoredSeaLevel(environment, seaLevel, absoluteStartHeight)
            ? seaLevel
            : generatedSurfaceY;
        return sourceWaterline + fittedStructureMinY - originalStructureMinY;
    }

    public static boolean preservesAuthoredSeaLevel(
        EnvironmentType environment,
        int seaLevel,
        Integer absoluteStartHeight
    ) {
        return environment == EnvironmentType.OCEAN_SURFACE
            && absoluteStartHeight != null
            && absoluteStartHeight > seaLevel;
    }
}
