package com.cappleapple.instancednotinfinite.client;

/** Monotonic presentation time; world time and server tick corrections are deliberately absent. */
final class AnimationClock {
    private static final double NANOS_PER_TICK = 50_000_000.0;
    private boolean initialized;
    private boolean paused;
    private long lastNanos;
    private double ticks;

    double advance(long nowNanos, boolean pause) {
        double elapsed = 0.0;
        if (initialized) {
            long delta = nowNanos - lastNanos;
            if (delta >= 0) {
                if (!paused && !pause) elapsed = delta / NANOS_PER_TICK;
                lastNanos = nowNanos;
            }
        } else {
            initialized = true;
            lastNanos = nowNanos;
        }
        paused = pause;
        ticks += elapsed;
        return elapsed;
    }

    double ticks() { return ticks; }

    static float approach(float current, float target, double elapsedTicks) {
        double blend = 1.0 - Math.pow(0.75, Math.max(0.0, elapsedTicks));
        return (float)(current + (target - current) * blend);
    }

    static float rotationDegrees(double ticks) {
        double degrees = ticks * 0.35;
        return (float)(degrees - Math.floor(degrees / 360.0) * 360.0);
    }
}
