package com.cappleapple.instancednotinfinite.recipe;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

@FunctionalInterface
public interface ItemTagLookup {
    List<ResourceLocation> items(ResourceLocation tagId);
}
