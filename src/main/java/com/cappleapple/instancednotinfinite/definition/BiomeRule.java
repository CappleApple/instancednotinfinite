package com.cappleapple.instancednotinfinite.definition;

public record BiomeRule(String reference, int weight) {
    public BiomeRule {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("biome reference must not be blank");
        }
        if (weight < 1) {
            throw new IllegalArgumentException("biome weight must be at least 1");
        }
    }

    public boolean tag() {
        return this.reference.startsWith("#");
    }

    public String id() {
        return this.tag() ? this.reference.substring(1) : this.reference;
    }
}
