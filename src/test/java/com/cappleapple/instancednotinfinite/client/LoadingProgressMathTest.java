package com.cappleapple.instancednotinfinite.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LoadingProgressMathTest {
    @Test
    void percentageUsesAnimationFraction() {
        assertEquals(0, LoadingProgressMath.percentage(0.0F));
        assertEquals(42, LoadingProgressMath.percentage(0.424F));
        assertEquals(100, LoadingProgressMath.percentage(1.0F));
    }

    @Test
    void percentageAndBarClampOutsideAnimationRange() {
        assertEquals(0, LoadingProgressMath.percentage(-0.25F));
        assertEquals(100, LoadingProgressMath.percentage(1.25F));
        assertEquals(0, LoadingProgressMath.filledWidth(-0.25F, 80));
        assertEquals(80, LoadingProgressMath.filledWidth(1.25F, 80));
        assertEquals(34, LoadingProgressMath.filledWidth(0.425F, 80));
    }
}
