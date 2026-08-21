package com.cappleapple.instancednotinfinite.definition;

public record HeightContext(int min, int max) {
    public HeightContext {
        if (min > max) {
            throw new IllegalArgumentException("height.min must not exceed height.max");
        }
        if (min < -2048 || max > 2048) {
            throw new IllegalArgumentException("height context must remain between -2048 and 2048");
        }
    }

    public int midpoint() {
        return min + (max - min) / 2;
    }
}
