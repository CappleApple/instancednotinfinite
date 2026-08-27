package com.cappleapple.instancednotinfinite.instance;

import java.util.function.LongSupplier;

/** One shared generation allowance per server tick, regardless of the job's trigger. */
public final class GenerationTickBudget {
    private final LongSupplier clock;
    private long tick = Long.MIN_VALUE;
    private long spentNanos;

    public GenerationTickBudget() {
        this(System::nanoTime);
    }

    GenerationTickBudget(LongSupplier clock) {
        this.clock = clock;
    }

    public boolean advance(long currentTick, double budgetMillis, int operationCap, Work work) throws InstanceOperationException {
        if (currentTick != tick) {
            tick = currentTick;
            spentNanos = 0;
        }
        long remaining = Math.max(1L, (long)(budgetMillis * 1_000_000.0)) - spentNanos;
        if (remaining <= 0) return false;
        long start = clock.getAsLong();
        try {
            work.advance(remaining / 1_000_000.0, operationCap);
        } finally {
            // A single chunk can exceed the allowance; do not start another job in that tick.
            spentNanos += Math.max(0, clock.getAsLong() - start);
        }
        return true;
    }

    @FunctionalInterface
    public interface Work {
        void advance(double remainingMillis, int operationCap) throws InstanceOperationException;
    }
}
