package com.cappleapple.instancednotinfinite.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TierSelectionTest {
    @Test
    void priorityAndThemeWinWhenRangesOverlap() {
        var ordinary = new TierSelection.Candidate<>("rare", "rare", 0, 0.55D, 0.75D, Set.of(), Set.of());
        var fire = new TierSelection.Candidate<>("fire_rare", "fire_rare", 10, 0.50D, 0.80D, Set.of("fire"), Set.of());

        assertEquals("fire_rare", TierSelection.select(0.62D, Set.of("fire"), Set.of(), List.of(ordinary, fire)).orElseThrow());
    }

    @Test
    void narrowerRangeBreaksEqualPriorityOverlap() {
        var broad = new TierSelection.Candidate<>("broad", "broad", 0, 0.0D, 1.0D, Set.of(), Set.of());
        var narrow = new TierSelection.Candidate<>("narrow", "narrow", 0, 0.55D, 0.75D, Set.of(), Set.of());

        assertEquals("narrow", TierSelection.select(0.60D, Set.of(), Set.of(), List.of(broad, narrow)).orElseThrow());
    }
}
