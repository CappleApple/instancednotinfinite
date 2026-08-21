package com.cappleapple.instancednotinfinite.recipe;

import net.minecraft.resources.ResourceLocation;

public record MaterialCandidate(ResourceLocation itemId, long occurrences, double score, String reason) {
    public MaterialCandidate {
        if (occurrences < 1) throw new IllegalArgumentException("Material occurrences must be positive");
    }
}
