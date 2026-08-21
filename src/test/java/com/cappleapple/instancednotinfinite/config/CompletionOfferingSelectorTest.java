package com.cappleapple.instancednotinfinite.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class CompletionOfferingSelectorTest {
    @Test
    void parsesExactItemsAndItemTags() {
        assertEquals(
            new CompletionOfferingSelector(false, "minecraft:blaze_powder"),
            CompletionOfferingSelector.parse("minecraft:blaze_powder").orElseThrow());
        assertEquals(
            new CompletionOfferingSelector(true, "example:portal_completion"),
            CompletionOfferingSelector.parse("#example:portal_completion").orElseThrow());
    }

    @Test
    void malformedSelectorsFailClosed() {
        assertFalse(CompletionOfferingSelector.parse("").isPresent());
        assertFalse(CompletionOfferingSelector.parse("#").isPresent());
        assertFalse(CompletionOfferingSelector.parse("not a resource id").isPresent());
        assertFalse(CompletionOfferingSelector.parse("#Uppercase:tag").isPresent());
    }
}
