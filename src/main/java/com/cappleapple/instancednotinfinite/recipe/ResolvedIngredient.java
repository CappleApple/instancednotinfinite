package com.cappleapple.instancednotinfinite.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

public record ResolvedIngredient(IngredientReference source, ResourceLocation selectedItem, Ingredient ingredient) {
}
