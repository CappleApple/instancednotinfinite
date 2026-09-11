package com.cappleapple.instancednotinfinite.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnimationClockTest {
    @Test
    void frameRateDoesNotChangeSpeedOrSmoothing() {
        for (int fps : new int[] {30, 60, 144}) {
            AnimationClock clock = new AnimationClock();
            long origin = -9_000_000_000L;
            clock.advance(origin, false);
            float progress = 0.0F;
            for (int frame = 1; frame <= fps; frame++) {
                double elapsed = clock.advance(origin + Math.round(frame * 1_000_000_000.0 / fps), false);
                assertTrue(elapsed > 0.0, "Every rendered frame must advance");
                progress = AnimationClock.approach(progress, 0.5F, elapsed);
                assertTrue(progress < 0.5F, "Smoothing must not overshoot generation readiness");
            }
            assertEquals(20.0, clock.ticks(), 0.000001);
            assertEquals(AnimationClock.approach(0.0F, 0.5F, 20.0), progress, 0.000001F);
        }
    }

    @Test
    void pauseAndResumeDoNotCatchUpPausedTime() {
        AnimationClock clock = new AnimationClock();
        clock.advance(0L, false);
        clock.advance(50_000_000L, false);
        assertEquals(0.0, clock.advance(60_000_000L, true));
        clock.advance(60_000_000_000L, true);
        assertEquals(0.0, clock.advance(60_050_000_000L, false));
        assertEquals(1.0, clock.ticks());
        assertEquals(1.0, clock.advance(60_100_000_000L, false));
        assertEquals(2.0, clock.ticks());
        assertEquals(0.25F, AnimationClock.approach(0.25F, 0.75F, 0.0));
    }

    @Test
    void backwardsSampleDoesNotRewindOrCountTimeTwice() {
        AnimationClock clock = new AnimationClock();
        clock.advance(100_000_000L, false);
        clock.advance(150_000_000L, false);
        assertEquals(0.0, clock.advance(125_000_000L, false));
        assertEquals(0.5, clock.advance(175_000_000L, false));
        assertEquals(1.5, clock.ticks());
    }

    @Test
    void nanoTimeWrapStillAdvancesNormally() {
        AnimationClock clock = new AnimationClock();
        long origin = Long.MAX_VALUE - 25_000_000L;
        clock.advance(origin, false);
        assertEquals(1.0, clock.advance(origin + 50_000_000L, false));
    }

    @Test
    void longSessionsKeepSubTickRotationPrecision() {
        AnimationClock clock = new AnimationClock();
        clock.advance(0L, false);
        long month = 30L * 24 * 60 * 60 * 1_000_000_000L;
        clock.advance(month, false);
        double before = clock.ticks();
        clock.advance(month + 8_333_333L, false);
        assertEquals(8_333_333.0 / 50_000_000.0, clock.ticks() - before, 0.000001);
        double beforeRotation = AnimationClock.rotationDegrees(before);
        double afterRotation = AnimationClock.rotationDegrees(clock.ticks());
        assertTrue(afterRotation >= 0.0 && afterRotation < 360.0);
        assertEquals((clock.ticks() - before) * 0.35,
            (afterRotation - beforeRotation + 360.0) % 360.0, 0.00002);
    }
}
