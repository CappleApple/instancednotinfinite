package com.cappleapple.instancednotinfinite.client;

/** Pure timing math for structure-pool icon crossfades and 3D miniature swaps. */
public final class PoolCatalystAnimation {
    private static final long ICON_FADE_MILLIS = 600L;

    private PoolCatalystAnimation() {
    }

    public static IconFrame iconFrame(long elapsedMillis, int count, long swapIntervalMillis) {
        requireCount(count);
        if (count == 1) return new IconFrame(0, 0, 0.0F);
        requireInterval(swapIntervalMillis);
        long fadeMillis = Math.min(ICON_FADE_MILLIS, swapIntervalMillis);
        long holdMillis = swapIntervalMillis - fadeMillis;
        long cycle = Math.floorDiv(elapsedMillis, swapIntervalMillis);
        long within = Math.floorMod(elapsedMillis, swapIntervalMillis);
        int current = Math.floorMod((int)cycle, count);
        int next = (current + 1) % count;
        float raw = within <= holdMillis ? 0.0F
            : (float)(within - holdMillis) / fadeMillis;
        float blend = raw * raw * (3.0F - 2.0F * raw);
        return new IconFrame(current, next, blend);
    }

    public static ModelFrame modelFrame(long elapsedMillis, int count, long swapIntervalMillis) {
        requireCount(count);
        if (count == 1) return new ModelFrame(0, 1.0F, slowRotation(elapsedMillis));
        requireInterval(swapIntervalMillis);
        long cycle = Math.floorDiv(elapsedMillis, swapIntervalMillis);
        double phase = (double)Math.floorMod(elapsedMillis, swapIntervalMillis) / swapIntervalMillis;
        int current = Math.floorMod((int)cycle, count);
        int next = (current + 1) % count;
        int index = phase < 0.5D ? current : next;
        float scale = (float)Math.abs(Math.cos(Math.PI * phase));
        return new ModelFrame(index, scale, slowRotation(elapsedMillis) + swapRotation(elapsedMillis, swapIntervalMillis));
    }

    private static float slowRotation(long elapsedMillis) {
        return (float)(Math.floorMod(elapsedMillis, 8_000L) * 360.0D / 8_000.0D);
    }

    private static float swapRotation(long elapsedMillis, long swapIntervalMillis) {
        long rotationCycle = swapIntervalMillis * 2L;
        return (float)(Math.floorMod(elapsedMillis, rotationCycle) * 180.0D / swapIntervalMillis);
    }

    private static void requireCount(int count) {
        if (count <= 0) throw new IllegalArgumentException("Pool animation requires at least one generated visual");
    }

    private static void requireInterval(long swapIntervalMillis) {
        if (swapIntervalMillis <= 0L) throw new IllegalArgumentException("Pool swap interval must be positive");
    }

    public record IconFrame(int currentIndex, int nextIndex, float blend) {
    }

    public record ModelFrame(int index, float scale, float rotationDegrees) {
    }
}
