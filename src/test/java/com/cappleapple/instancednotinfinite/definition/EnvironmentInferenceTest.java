package com.cappleapple.instancednotinfinite.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnvironmentInferenceTest {
    @Test
    void biomeDimensionsTakePriority() {
        assertEquals(EnvironmentType.NETHER_LIKE, classify(true, false, false, "none", "surface_structures"));
        assertEquals(EnvironmentType.END_LIKE, classify(false, true, false, "none", "surface_structures"));
        assertEquals(EnvironmentType.UNDERWATER, classify(false, false, true, "none", "surface_structures"));
        assertEquals(EnvironmentType.OCEAN_SURFACE, classify(false, false, true, "none", "top_layer_modification"));
    }

    @Test
    void highAbsoluteOceanStructureIsKeptAboveWater() {
        assertEquals(
            EnvironmentType.OCEAN_SURFACE,
            classify(false, false, true, "none", "surface_structures", 200),
            "Cataclysm Acropolis is authored at absolute Y=200 and must not be submerged");
        assertEquals(
            EnvironmentType.UNDERWATER,
            classify(false, false, true, "none", "surface_structures", 32),
            "Low-authored ocean structures must remain submerged");
    }

    @Test
    void terrainMetadataIdentifiesBuriedAndCaveStructures() {
        assertEquals(EnvironmentType.UNDERGROUND, classify(false, false, false, "bury", "surface_structures"));
        assertEquals(EnvironmentType.CAVE, classify(false, false, false, "encapsulate", "surface_structures"));
        assertEquals(EnvironmentType.CAVE, classify(false, false, false, "none", "underground_structures"));
    }

    @Test
    void strongholdGenerationStepUsesUndergroundHandling() {
        assertEquals(
            EnvironmentType.UNDERGROUND,
            classify(false, false, false, "none", "strongholds", 15),
            "Integrated Stronghold must use the enclosed underground terrain and entry pipeline");
    }

    @Test
    void lowConfidenceFallsBackSafelyToSurface() {
        assertEquals(EnvironmentType.SURFACE, classify(false, false, false, "none", "surface_structures"));
    }

    private static EnvironmentType classify(boolean nether, boolean end, boolean ocean, String adaptation, String step) {
        return classify(nether, end, ocean, adaptation, step, null);
    }

    private static EnvironmentType classify(
        boolean nether,
        boolean end,
        boolean ocean,
        String adaptation,
        String step,
        Integer absoluteStartHeight
    ) {
        return EnvironmentInference.classify(
            new EnvironmentInference.Evidence(nether, end, ocean, adaptation, step, absoluteStartHeight)).environment();
    }
}
