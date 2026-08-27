package com.cappleapple.instancednotinfinite.definition;

/** Refines catalogue hints from the actual start produced against a flat biome terrain sample. */
public final class PlacementEnvironmentInference {
    private PlacementEnvironmentInference() {
    }

    public static EnvironmentType classify(
        EnvironmentType hint, int minimumY, int maximumY, int groundY,
        int sampledSurfaceY, Integer sampledOceanFloorY, boolean usedOceanFloor
    ) {
        if (hint == EnvironmentType.CUSTOM || hint == EnvironmentType.CAVE || hint == EnvironmentType.UNDERGROUND) {
            return hint;
        }
        // Use the lowest actual piece, not an adaptation-expanded bounding box or a tall roof.
        if (minimumY >= sampledSurfaceY + 8) return EnvironmentType.FLOATING_ISLAND;
        if (sampledOceanFloorY != null) {
            if (usedOceanFloor) return EnvironmentType.UNDERWATER;
            int midpoint = sampledOceanFloorY + (sampledSurfaceY - sampledOceanFloorY) / 2;
            return maximumY > sampledSurfaceY && groundY > midpoint
                ? EnvironmentType.OCEAN_SURFACE : EnvironmentType.UNDERWATER;
        }
        return hint;
    }
}
