package com.cappleapple.instancednotinfinite.instance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class GenerationTickBudgetTest {
    @Test
    void differentTriggersShareTheRemainingAllowance() throws Exception {
        AtomicLong clock = new AtomicLong();
        GenerationTickBudget budget = new GenerationTickBudget(clock::get);
        assertTrue(budget.advance(10, 4, 5000, (millis, operations) -> {
            assertEquals(4.0, millis);
            assertEquals(5000, operations);
            clock.addAndGet(3_000_000);
        }));
        assertTrue(budget.advance(10, 4, 5000, (millis, operations) -> {
            assertEquals(1.0, millis);
            clock.addAndGet(1_000_000);
        }));
        assertFalse(budget.advance(10, 4, 5000, (millis, operations) -> fail("Budget was reset for another trigger")));
    }

    @Test
    void oversizedChunkDefersOtherJobsUntilTheNextTick() throws Exception {
        AtomicLong clock = new AtomicLong();
        GenerationTickBudget budget = new GenerationTickBudget(clock::get);
        budget.advance(10, 4, 1, (millis, operations) -> clock.addAndGet(25_000_000));
        assertFalse(budget.advance(10, 4, 1, (millis, operations) -> fail("Another chunk started after an overrun")));
        assertTrue(budget.advance(11, 4, 1, (millis, operations) -> assertEquals(4.0, millis)));
    }

    @Test
    void failedWorkStillConsumesItsTime() throws Exception {
        AtomicLong clock = new AtomicLong();
        GenerationTickBudget budget = new GenerationTickBudget(clock::get);
        assertThrows(InstanceOperationException.class, () -> budget.advance(10, 4, 5000, (millis, operations) -> {
            clock.addAndGet(4_000_000);
            throw new InstanceOperationException("Fixture failure");
        }));
        assertFalse(budget.advance(10, 4, 5000, (millis, operations) -> fail("Failed work was not charged")));
    }
}
