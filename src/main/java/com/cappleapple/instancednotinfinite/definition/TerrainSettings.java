package com.cappleapple.instancednotinfinite.definition;

public record TerrainSettings(int horizontalPadding, int verticalPadding, int maximumRadius) {
    public TerrainSettings {
        if (horizontalPadding < 0 || horizontalPadding > 512) {
            throw new IllegalArgumentException("terrain.horizontalPadding must be between 0 and 512");
        }
        if (verticalPadding < 0 || verticalPadding > 256) {
            throw new IllegalArgumentException("terrain.verticalPadding must be between 0 and 256");
        }
        if (maximumRadius < 16 || maximumRadius > 1024) {
            throw new IllegalArgumentException("terrain.maximumRadius must be between 16 and 1024");
        }
    }
}
