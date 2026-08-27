package com.cappleapple.instancednotinfinite.recipe;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** A generated recipe profile plus the datapack resources that materially produced it. */
public record StructureRecipeAnalysis(
    StructureRecipeProfile profile,
    Set<ResourceLocation> resourceDependencies
) {
    public StructureRecipeAnalysis {
        resourceDependencies = Set.copyOf(resourceDependencies);
    }
}
