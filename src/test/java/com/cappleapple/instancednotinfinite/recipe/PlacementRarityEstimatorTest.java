package com.cappleapple.instancednotinfinite.recipe;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlacementRarityEstimatorTest {
    @Test
    void widerSpacingAndLowerWeightIncreaseRarity() {
        double common = PlacementRarityEstimator.randomSpread(24, 1.0D, 1, 1, false);
        double rare = PlacementRarityEstimator.randomSpread(64, 0.5D, 1, 4, true);
        assertTrue(rare > common);
    }

    @Test
    void sizeInfluenceCannotDominatePlacement() {
        double smallRare = PlacementRarityEstimator.combine(0.9D, 0.0D, false);
        double hugeCommon = PlacementRarityEstimator.combine(0.2D, 1.0D, true);
        assertTrue(smallRare > hugeCommon);
    }
}
