package com.cappleapple.instancednotinfinite.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceDimensionIdsTest {
    @Test
    void recognizesOnlyOwnedTemporaryInstancePaths() {
        assertTrue(InstanceDimensionIds.isTemporaryInstance("instancednotinfinite", "instances/1234"));
        assertFalse(InstanceDimensionIds.isTemporaryInstance("instancednotinfinite", "instances"));
        assertFalse(InstanceDimensionIds.isTemporaryInstance("instancednotinfinite", "other/1234"));
        assertFalse(InstanceDimensionIds.isTemporaryInstance("another_mod", "instances/1234"));
    }
}
