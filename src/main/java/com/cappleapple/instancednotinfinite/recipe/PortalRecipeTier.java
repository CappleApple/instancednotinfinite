package com.cappleapple.instancednotinfinite.recipe;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record PortalRecipeTier(
    ResourceLocation id,
    int priority,
    double rarityMin,
    double rarityMax,
    IngredientReference core,
    Set<RecipeTheme> requiredThemes,
    Set<RecipeArchetype> requiredArchetypes
) {
    public PortalRecipeTier {
        if (rarityMin < 0.0D || rarityMax > 1.0D || rarityMin > rarityMax) {
            throw new IllegalArgumentException("Tier rarity range must be within 0..1 and min <= max");
        }
        requiredThemes = Set.copyOf(requiredThemes);
        requiredArchetypes = Set.copyOf(requiredArchetypes);
    }

    public boolean matches(StructureRecipeProfile profile) {
        return profile.rarityScore() >= rarityMin
            && profile.rarityScore() <= rarityMax
            && profile.themes().containsAll(requiredThemes)
            && profile.archetypes().containsAll(requiredArchetypes);
    }

    public double width() {
        return rarityMax - rarityMin;
    }
}
