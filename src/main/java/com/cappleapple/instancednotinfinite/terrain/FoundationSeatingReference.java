package com.cappleapple.instancednotinfinite.terrain;

/** Chooses how much authored foundation remains below terrain for a structure's placement mode. */
public final class FoundationSeatingReference {
    private FoundationSeatingReference() {
    }

    public static int select(
        DominantFoundationLevel.FoundationSpan foundation,
        int placementGroundY,
        boolean adaptsTerrain
    ) {
        if (!adaptsTerrain) {
            return foundation.baseY();
        }
        return Math.max(foundation.topY() + 1, placementGroundY);
    }
}
