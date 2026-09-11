package com.cappleapple.instancednotinfinite.client;

/** One sample per rendered frame, shared by world effects, item previews, and visual targeting. */
final class ClientAnimationTime {
    private static AnimationClock clock = new AnimationClock();

    private ClientAnimationTime() {
    }

    static double advance(long nowNanos, boolean paused) { return clock.advance(nowNanos, paused); }
    static double ticks() { return clock.ticks(); }
    static long millis() { return (long)(clock.ticks() * 50.0); }
    static void reset() { clock = new AnimationClock(); }
}
