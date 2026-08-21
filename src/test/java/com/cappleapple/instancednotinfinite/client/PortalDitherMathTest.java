package com.cappleapple.instancednotinfinite.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PortalDitherMathTest {
    @Test
    void portalContractsOnlyToConfiguredMinimum() {
        assertEquals(1.0F, PortalDitherMath.portalSizeScale(1.0F, 0.25F));
        assertEquals(0.25F, PortalDitherMath.portalSizeScale(0.0F, 0.25F));
        assertTrue(PortalDitherMath.portalSizeScale(0.25F, 0.25F)
            < PortalDitherMath.portalSizeScale(0.75F, 0.25F));
    }

    @Test
    void defaultPortalEndsAtOneByTwoDoorway() {
        float widthScale = PortalDitherMath.portalSizeScale(0.0F, 1.0F / 1.5F);
        float heightScale = PortalDitherMath.portalSizeScale(0.0F, 2.0F / 2.5F);
        assertEquals(1.0F, 1.5F * widthScale, 0.0001F);
        assertEquals(2.0F, 2.5F * heightScale, 0.0001F);
    }

    @Test
    void centerMovesDownToKeepBottomFixed() {
        float fullHalfHeight = 1.25F;
        float scale = 0.25F;
        float center = PortalDitherMath.fixedBottomCenterOffset(fullHalfHeight, scale);
        assertEquals(-fullHalfHeight, center - fullHalfHeight * scale);
    }

    @Test
    void fragmentsShrinkWithAgeAndFinalCollapse() {
        assertTrue(PortalDitherMath.fragmentScale(0.25F, 0.0F)
            > PortalDitherMath.fragmentScale(0.75F, 0.0F));
        assertEquals(0.0F, PortalDitherMath.fragmentScale(1.0F, 0.0F));
        assertEquals(0.0F, PortalDitherMath.fragmentScale(0.0F, 1.0F));
    }

    @Test
    void fragmentsFadeFromInsideToTheEdgeBeforeTheirExistingMotion() {
        assertEquals(0.0F, PortalDitherMath.fragmentSpawnFade(0.0F));
        assertEquals(0.0F, PortalDitherMath.fragmentAnimationAge(0.1F));
        assertTrue(PortalDitherMath.fragmentSpawnInset(0.0F, 0.06F) > 0.0F);
        assertEquals(0.0F, PortalDitherMath.fragmentAlpha(0.0F, 0.0F));

        float fullySpawnedAge = 0.18F;
        assertEquals(1.0F, PortalDitherMath.fragmentSpawnFade(fullySpawnedAge), 0.0001F);
        assertEquals(0.0F, PortalDitherMath.fragmentSpawnInset(fullySpawnedAge, 0.06F), 0.0001F);
        assertEquals(0.0F, PortalDitherMath.fragmentAnimationAge(fullySpawnedAge), 0.0001F);
        assertEquals(1.0F, PortalDitherMath.fragmentAlpha(fullySpawnedAge, 0.0F), 0.0001F);

        assertTrue(PortalDitherMath.fragmentAnimationAge(0.5F) > 0.0F);
        assertEquals(0.0F, PortalDitherMath.fragmentAlpha(1.0F, 0.0F));
    }

    @Test
    void dissolveStartsAlwaysLeaveAtZeroAndFinishAtOne() {
        for (int row = 0; row < 14; row++) {
            for (int column = 0; column < 10; column++) {
                float start = PortalDitherMath.dissolveStart(column, row, 10, 14);
                assertTrue(start > 0.0F);
                assertTrue(start < 1.0F);
                assertEquals(0.0F, PortalDitherMath.disperseProgress(0.0F, start));
                assertEquals(1.0F, PortalDitherMath.disperseProgress(1.0F, start));
            }
        }
    }

    @Test
    void dissolvingTilesTravelThenShrinkAway() {
        float start = 0.35F;
        assertEquals(0.0F, PortalDitherMath.disperseProgress(start, start));
        float halfway = PortalDitherMath.disperseProgress(0.675F, start);
        assertEquals(0.5F, halfway, 0.0001F);
        assertTrue(PortalDitherMath.disperseScale(halfway) < 1.0F);
        assertEquals(0.0F, PortalDitherMath.disperseScale(1.0F));
    }
}
