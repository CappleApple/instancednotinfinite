package com.cappleapple.instancednotinfinite.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PortalRecipeAnalysisPlanningTest {
    private static final String PREDEFINED = "predefined";
    private static final String AUTOMATIC = "automatic";
    private static final String EXCLUDED = "excluded";
    private static final String OVERRIDDEN = "overridden";
    private static final String POOL_ONLY = "pool_only";

    @Test
    void predefinedAndSuppressedTargetsNeverReachAutomaticAnalysis() {
        assertEquals(Set.of(AUTOMATIC, OVERRIDDEN), PortalRecipeAnalysisPlanner.automaticAnalysisTargets(
            List.of(PREDEFINED, AUTOMATIC, EXCLUDED, OVERRIDDEN, POOL_ONLY),
            Set.of(PREDEFINED),
            Set.of(OVERRIDDEN),
            Set.of(EXCLUDED, OVERRIDDEN),
            Set.of(POOL_ONLY),
            Set.of(),
            true));
    }

    @Test
    void poolOnlyMemberIsAnalyzedOnlyWhileAnAutomaticPoolRecipeNeedsIt() {
        assertEquals(Set.of(), PortalRecipeAnalysisPlanner.automaticAnalysisTargets(
            List.of(POOL_ONLY), Set.of(), Set.of(), Set.of(), Set.of(POOL_ONLY), Set.of(), true));
        assertEquals(Set.of(POOL_ONLY), PortalRecipeAnalysisPlanner.automaticAnalysisTargets(
            List.of(POOL_ONLY), Set.of(), Set.of(), Set.of(), Set.of(POOL_ONLY), Set.of(POOL_ONLY), true));
    }

    @Test
    void disablingAutomaticRecipesSkipsAllAnalysis() {
        assertEquals(Set.of(), PortalRecipeAnalysisPlanner.automaticAnalysisTargets(
            List.of(AUTOMATIC), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false));
    }
}
