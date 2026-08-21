package com.cappleapple.instancednotinfinite.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InstanceVacancyPolicyTest {
    @Test
    void neverEnteredUsesGeneralVacancyTimeout() {
        assertEquals(300, InstanceVacancyPolicy.timeoutSeconds(false, 300, 60));
    }

    @Test
    void enteredAndLeftUsesPostVisitTimeout() {
        assertEquals(60, InstanceVacancyPolicy.timeoutSeconds(true, 300, 60));
    }

    @Test
    void zeroExpiresImmediately() {
        assertTrue(InstanceVacancyPolicy.expired(1_000L, 1_000L, 0));
    }

    @Test
    void countdownRoundsPartialSecondsToClientTicks() {
        assertEquals(1, InstanceVacancyPolicy.remainingTicks(1_000L, 1_951L, 1));
        assertFalse(InstanceVacancyPolicy.expired(1_000L, 1_999L, 1));
        assertTrue(InstanceVacancyPolicy.expired(1_000L, 2_000L, 1));
    }

    @Test
    void negativeOneNeverExpires() {
        assertEquals(-1, InstanceVacancyPolicy.timeoutSeconds(false, -1, 60));
        assertEquals(-1, InstanceVacancyPolicy.timeoutSeconds(true, 300, -1));
        assertFalse(InstanceVacancyPolicy.expired(1_000L, Long.MAX_VALUE, -1));
        assertEquals(Integer.MAX_VALUE, InstanceVacancyPolicy.remainingTicks(1_000L, Long.MAX_VALUE, -1));
    }
}
