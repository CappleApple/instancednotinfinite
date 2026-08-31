package com.cappleapple.instancednotinfinite.structure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ControlledStructureStartGenerationTest {
    @Test
    void scopeIsNarrowNonNestedAndClearedOnClose() {
        assertFalse(ControlledStructureStartGeneration.isActive());
        try (var ignored = ControlledStructureStartGeneration.begin()) {
            assertTrue(ControlledStructureStartGeneration.isActive());
            assertThrows(IllegalStateException.class, ControlledStructureStartGeneration::begin);
        }
        assertFalse(ControlledStructureStartGeneration.isActive());
    }
}
