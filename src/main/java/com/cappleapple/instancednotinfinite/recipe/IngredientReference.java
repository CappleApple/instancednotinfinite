package com.cappleapple.instancednotinfinite.recipe;

import net.minecraft.resources.ResourceLocation;

/** An item ID or item-tag ID as written in tier/config data. */
public record IngredientReference(boolean tag, ResourceLocation id) {
    public static IngredientReference parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ingredient reference is blank");
        }
        boolean tag = value.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? value.substring(1) : value);
        if (id == null) {
            throw new IllegalArgumentException("invalid ingredient reference '" + value + "'");
        }
        return new IngredientReference(tag, id);
    }

    @Override
    public String toString() {
        return (tag ? "#" : "") + id;
    }
}
