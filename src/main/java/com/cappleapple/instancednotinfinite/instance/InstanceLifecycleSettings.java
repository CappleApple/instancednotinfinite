package com.cappleapple.instancednotinfinite.instance;

/** Concrete per-instance cleanup deadlines. A value of -1 disables that deadline. */
public record InstanceLifecycleSettings(
    int openSeconds,
    int postVisitSeconds,
    int forceCollapseSeconds
) {
    public static final int INFINITE = -1;
    public static final int MAX_SECONDS = 31_536_000;
    public static final InstanceLifecycleSettings DEFAULT = new InstanceLifecycleSettings(300, 60, INFINITE);

    public InstanceLifecycleSettings {
        validate(openSeconds, "openSeconds");
        validate(postVisitSeconds, "postVisitSeconds");
        validate(forceCollapseSeconds, "forceCollapseSeconds");
    }

    public int vacancySeconds(boolean everEntered) {
        return everEntered ? postVisitSeconds : openSeconds;
    }

    public boolean forceCollapseExpired(long openedAtMillis, long nowMillis) {
        return forceCollapseSeconds != INFINITE
            && nowMillis - openedAtMillis >= forceCollapseSeconds * 1_000L;
    }

    private static void validate(int value, String name) {
        if (value < INFINITE || value > MAX_SECONDS) {
            throw new IllegalArgumentException(name + " must be -1 or between 0 and " + MAX_SECONDS);
        }
    }
}
