package com.cappleapple.instancednotinfinite.manifestation;

public enum AnimationMode {
    GROUND_UP,
    MIDDLE_OUT,
    OUTSIDE_IN,
    SINGLE_ORIGIN,
    MULTI_ORIGIN,
    CHAOTIC,
    RANDOM_ORDER,
    NONE,
    RANDOM_MODE;

    public static AnimationMode parse(String value) {
        return AnimationMode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
