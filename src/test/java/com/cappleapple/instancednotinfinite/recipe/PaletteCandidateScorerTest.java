package com.cappleapple.instancednotinfinite.recipe;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaletteCandidateScorerTest {
    @Test
    void excludesTechnicalBlocks() {
        assertTrue(PaletteCandidateScorer.excludedPath("jigsaw"));
        assertTrue(PaletteCandidateScorer.excludedPath("modded_trial_spawner"));
    }

    @Test
    void distinctiveSameModMaterialOutranksGenericFiller() {
        double stone = PaletteCandidateScorer.candidateScore("test", "minecraft", "stone", 1_000L, 1_000L);
        double ancientTiles = PaletteCandidateScorer.candidateScore("test", "test", "ancient_tiles", 250L, 1_000L);
        assertTrue(ancientTiles > stone);
    }
}
