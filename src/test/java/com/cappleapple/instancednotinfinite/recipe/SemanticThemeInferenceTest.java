package com.cappleapple.instancednotinfinite.recipe;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SemanticThemeInferenceTest {
    @Test
    void burningArenaAddsFireAndBossSignals() {
        ThemeInferenceResult result = SemanticThemeInference.fromStructureName("burning_arena");

        assertTrue(result.themes().contains(RecipeTheme.FIRE));
        assertTrue(result.themes().contains(RecipeTheme.COMBAT));
        assertTrue(result.archetypes().contains(RecipeArchetype.ARENA));
        assertTrue(result.archetypes().contains(RecipeArchetype.BOSS));
    }

    @Test
    void tokenizationDoesNotInferFromUnrelatedSubstrings() {
        ThemeInferenceResult result = SemanticThemeInference.fromStructureName("endurance_hall");
        assertTrue(result.themes().isEmpty());
    }
}
