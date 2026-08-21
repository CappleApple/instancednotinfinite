package com.cappleapple.instancednotinfinite.definition;

public record EntryPoint(int x, int y, int z, float yaw, float pitch) {
    public EntryPoint {
        if (x < -4096 || x > 4096 || y < -2048 || y > 2048 || z < -4096 || z > 4096) {
            throw new IllegalArgumentException("entry offset is outside the supported range");
        }
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("entry rotation must be finite");
        }
    }
}
