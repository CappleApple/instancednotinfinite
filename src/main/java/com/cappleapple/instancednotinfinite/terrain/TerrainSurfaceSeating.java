package com.cappleapple.instancednotinfinite.terrain;

import com.cappleapple.instancednotinfinite.definition.EnvironmentType;

/** Pure vertical seating rule shared by planning and unit tests. */
public final class TerrainSurfaceSeating {
    private TerrainSurfaceSeating() {
    }

    /** A measured authored foundation is stronger evidence than a generic flat sampling height. */
    public static int seatToFoundation(EnvironmentType environment, int foundationReferenceY, int generatedSurfaceY) {
        if (environment == EnvironmentType.SURFACE || environment == EnvironmentType.NETHER_LIKE) {
            return Math.min(generatedSurfaceY, foundationReferenceY - 1);
        }
        return generatedSurfaceY;
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
