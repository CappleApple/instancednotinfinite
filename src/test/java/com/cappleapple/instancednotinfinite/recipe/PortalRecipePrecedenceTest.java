package com.cappleapple.instancednotinfinite.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PortalRecipePrecedenceTest {
    @Test
    void datapackRecipeAlwaysWins() {
        assertEquals(RecipeSource.DATAPACK, PortalRecipePrecedence.choose(true, true, true, false));
    }

    @Test
    void overrideWinsBeforeAutomaticInference() {
        assertEquals(RecipeSource.EXPLICIT_OVERRIDE, PortalRecipePrecedence.choose(false, true, true, false));
    }

    @Test
    void failedInferenceUsesGenericFallback() {
        assertEquals(RecipeSource.GENERIC_FALLBACK, PortalRecipePrecedence.choose(false, false, true, true));
    }
}
