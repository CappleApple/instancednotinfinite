package com.cappleapple.instancednotinfinite.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PoolCatalystAnimationTest {
    private static final long FIVE_SECONDS = 5_000L;

    @Test
    void iconsHoldThenCrossfadeToNextGeneratedIcon() {
        var held = PoolCatalystAnimation.iconFrame(4_000L, 3, FIVE_SECONDS);
        var fading = PoolCatalystAnimation.iconFrame(4_700L, 3, FIVE_SECONDS);
        var next = PoolCatalystAnimation.iconFrame(5_000L, 3, FIVE_SECONDS);
        var following = PoolCatalystAnimation.iconFrame(10_000L, 3, FIVE_SECONDS);

        assertEquals(0, held.currentIndex());
        assertEquals(0.0F, held.blend());
        assertEquals(0.5F, fading.blend(), 0.0001F);
        assertEquals(1, next.currentIndex());
        assertEquals(2, following.currentIndex());
    }

    @Test
    void modelShrinksSwapsAndExpands() {
        var full = PoolCatalystAnimation.modelFrame(0L, 3, FIVE_SECONDS);
        var swap = PoolCatalystAnimation.modelFrame(2_500L, 3, FIVE_SECONDS);
        var expanding = PoolCatalystAnimation.modelFrame(3_750L, 3, FIVE_SECONDS);
        var beforeFollowingSwap = PoolCatalystAnimation.modelFrame(7_499L, 3, FIVE_SECONDS);
        var followingSwap = PoolCatalystAnimation.modelFrame(7_500L, 3, FIVE_SECONDS);

        assertEquals(0, full.index());
        assertEquals(1.0F, full.scale(), 0.0001F);
        assertEquals(1, swap.index());
        assertEquals(0.0F, swap.scale(), 0.0001F);
        assertEquals(1, expanding.index());
        assertTrue(expanding.scale() > swap.scale());
        assertEquals(1, beforeFollowingSwap.index());
        assertEquals(2, followingSwap.index());
    }

    @Test
    void modelRotationIsContinuousAcrossSwapIntervals() {
        var beforeBoundary = PoolCatalystAnimation.modelFrame(4_999L, 3, FIVE_SECONDS);
        var atBoundary = PoolCatalystAnimation.modelFrame(5_000L, 3, FIVE_SECONDS);

        assertTrue(angularDistance(beforeBoundary.rotationDegrees(), atBoundary.rotationDegrees()) < 1.0F,
            "full-sized pool miniature rotation jumped at the interval boundary");
    }

    private static float angularDistance(float first, float second) {
        float delta = Math.floorMod(Math.round(second - first), 360);
        return Math.min(delta, 360.0F - delta);
    }
}
