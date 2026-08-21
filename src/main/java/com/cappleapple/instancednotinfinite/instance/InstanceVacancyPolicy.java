package com.cappleapple.instancednotinfinite.instance;

/** Chooses and evaluates the configured cleanup timeout for an empty dungeon instance. */
public final class InstanceVacancyPolicy {
    private InstanceVacancyPolicy() {
    }

    public static int timeoutSeconds(boolean everEntered, int vacancySeconds, int postVisitSeconds) {
        if (vacancySeconds < InstanceLifecycleSettings.INFINITE || postVisitSeconds < InstanceLifecycleSettings.INFINITE) {
            throw new IllegalArgumentException("Vacancy timeouts must be -1 or non-negative");
        }
        return everEntered ? postVisitSeconds : vacancySeconds;
    }

    public static boolean expired(long vacantSinceMillis, long nowMillis, int timeoutSeconds) {
        if (timeoutSeconds == InstanceLifecycleSettings.INFINITE) return false;
        if (timeoutSeconds < 0) throw new IllegalArgumentException("timeoutSeconds must be -1 or non-negative");
        return nowMillis - vacantSinceMillis >= timeoutSeconds * 1_000L;
    }

    public static int remainingTicks(long vacantSinceMillis, long nowMillis, int timeoutSeconds) {
        if (timeoutSeconds == InstanceLifecycleSettings.INFINITE) return Integer.MAX_VALUE;
        if (timeoutSeconds < 0) throw new IllegalArgumentException("timeoutSeconds must be -1 or non-negative");
        long remainingMillis = Math.max(0L, timeoutSeconds * 1_000L - Math.max(0L, nowMillis - vacantSinceMillis));
        return (int)Math.min(Integer.MAX_VALUE, (remainingMillis + 49L) / 50L);
    }
}
