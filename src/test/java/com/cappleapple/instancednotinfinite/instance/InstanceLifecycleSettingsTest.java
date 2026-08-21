package com.cappleapple.instancednotinfinite.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InstanceLifecycleSettingsTest {
    @Test
    void selectsTheAppropriateVacancyDeadline() {
        InstanceLifecycleSettings settings = new InstanceLifecycleSettings(-1, 60, 3600);
        assertEquals(-1, settings.vacancySeconds(false));
        assertEquals(60, settings.vacancySeconds(true));
    }

    @Test
    void allInfiniteSettingsHaveNoAutomaticDeadline() {
        InstanceLifecycleSettings settings = new InstanceLifecycleSettings(-1, -1, -1);
        assertEquals(-1, settings.vacancySeconds(false));
        assertEquals(-1, settings.vacancySeconds(true));
        assertFalse(settings.forceCollapseExpired(0L, Long.MAX_VALUE));
    }

    @Test
    void forceCollapseUsesReadinessTime() {
        InstanceLifecycleSettings settings = new InstanceLifecycleSettings(300, 60, 10);
        assertFalse(settings.forceCollapseExpired(1_000L, 10_999L));
        assertTrue(settings.forceCollapseExpired(1_000L, 11_000L));
    }

    @Test
    void rejectsValuesBelowInfiniteSentinel() {
        assertThrows(IllegalArgumentException.class, () -> new InstanceLifecycleSettings(-2, 0, 0));
    }
}
