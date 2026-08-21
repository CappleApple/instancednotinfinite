package com.cappleapple.instancednotinfinite.terrain;

import com.cappleapple.instancednotinfinite.definition.EnvironmentType;

/** Pure vertical seating rule shared by planning and unit tests. */
public final class TerrainSurfaceSeating {
    private TerrainSurfaceSeating() {
    }

    public static int seat(
        EnvironmentType environment,
        int structureMinY,
        int structureMaxY,
        int generatedSurfaceY
    ) {
        return seat(environment, structureMinY, structureMaxY, structureMinY, generatedSurfaceY);
    }

    public static int seat(
        EnvironmentType environment,
        int structureMinY,
        int structureMaxY,
        int foundationY,
        int generatedSurfaceY
    ) {
        int structureHeight = structureMaxY - structureMinY + 1;
        if (structureHeight >= 48
            && (environment == EnvironmentType.SURFACE || environment == EnvironmentType.NETHER_LIKE)
            && generatedSurfaceY >= structureMaxY + 2) {
            return Math.max(structureMinY, Math.min(structureMaxY, foundationY)) - 1;
        }
        return generatedSurfaceY;
    }
}
