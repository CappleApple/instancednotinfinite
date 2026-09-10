package com.cappleapple.instancednotinfinite.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class GenerationProgressTest {
    @Test
    void completedJobsReachExactlyOneForEveryWorkCount() {
        for (int total = 1; total <= 20_000; total++) {
            assertEquals(1.0, GenerationProgress.fraction(total, total, true),
                "Completed job must not remain a rounding error below 100%: " + total);
        }
    }

    @Test
    void preparationAndFinalizationRemainBelowCompletion() {
        assertEquals(0.05, GenerationProgress.fraction(0, 12, false));
        for (int total : new int[]{3, 12, 41, 512, 20_000}) {
            double previous = 0;
            for (int done = 0; done <= total; done++) {
                double progress = GenerationProgress.fraction(done, total, false);
                assertTrue(progress >= previous, "Progress moved backwards");
                assertTrue(progress < 1.0, "Unfinished job reported completion");
                previous = progress;
            }
        }
    }
}
