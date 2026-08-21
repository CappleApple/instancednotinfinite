package com.cappleapple.instancednotinfinite.manifestation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ManifestationScorerTest {
    @Test
    void scoresAreDeterministicAndNormalized() {
        for (AnimationMode mode : AnimationMode.values()) {
            if (mode == AnimationMode.RANDOM_MODE) continue;
            double first = score(7, 14, 21, mode, 987654321L);
            assertEquals(first, score(7, 14, 21, mode, 987654321L));
            assertTrue(first >= 0.0 && first <= 1.0, mode.name());
        }
    }

    @Test
    void groundUpHasUnevenSpatialFront() {
        double a = score(2, 12, 2, AnimationMode.GROUND_UP, 42L);
        double b = score(26, 12, 26, AnimationMode.GROUND_UP, 42L);
        assertNotEquals(a, b);
    }

    @Test
    void randomModeSelectionIsStable() {
        List<AnimationMode> modes = List.of(AnimationMode.GROUND_UP, AnimationMode.MIDDLE_OUT, AnimationMode.CHAOTIC);
        assertEquals(
            ManifestationScorer.resolveMode(AnimationMode.RANDOM_MODE, 1234L, modes),
            ManifestationScorer.resolveMode(AnimationMode.RANDOM_MODE, 1234L, modes));
    }

    @Test
    void firstCapturedScoreStartsRevealWithoutChangingOrder() {
        assertEquals(0.0, ManifestationScoreMath.normalizeRevealScore(0.4, 0.4));
        assertEquals(0.5, ManifestationScoreMath.normalizeRevealScore(0.7, 0.4), 0.000001);
        assertEquals(1.0, ManifestationScoreMath.normalizeRevealScore(1.0, 0.4));
        assertEquals(0.0, ManifestationScoreMath.normalizeRevealScore(0.2, 0.4));
    }

    private static double score(int x, int y, int z, AnimationMode mode, long seed) {
        return ManifestationScoreMath.score(x, y, z, 0, 0, 0, 31, 31, 31, mode, seed);
    }
}
