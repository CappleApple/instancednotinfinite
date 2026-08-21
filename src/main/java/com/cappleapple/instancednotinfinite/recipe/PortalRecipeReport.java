package com.cappleapple.instancednotinfinite.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public record PortalRecipeReport(
    ResourceLocation dungeonId,
    RecipeSource source,
    StructureRecipeProfile profile,
    Optional<ResourceLocation> recipeId,
    Optional<PortalRecipeTier> tier,
    Optional<ResolvedIngredient> signature,
    Optional<ResolvedIngredient> theme,
    Optional<ResolvedIngredient> core,
    Optional<ResolvedIngredient> catalyst,
    List<String> warnings
) {
    public PortalRecipeReport {
        recipeId = recipeId == null ? Optional.empty() : recipeId;
        tier = tier == null ? Optional.empty() : tier;
        signature = signature == null ? Optional.empty() : signature;
        theme = theme == null ? Optional.empty() : theme;
        core = core == null ? Optional.empty() : core;
        catalyst = catalyst == null ? Optional.empty() : catalyst;
        warnings = List.copyOf(warnings);
    }

    public List<String> explanationLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Dungeon: " + dungeonId);
        lines.add("Recipe source: " + source);
        recipeId.ifPresent(id -> lines.add("Recipe: " + id));
        lines.add("Rarity score: " + String.format(Locale.ROOT, "%.2f", profile.rarityScore()));
        lines.add("Size score: " + String.format(Locale.ROOT, "%.2f", profile.sizeScore()));
        lines.add("Resolved tier: " + tier.map(value -> value.id().toString()).orElse("none"));
        lines.add("Detected themes: " + profile.themes().stream().map(RecipeTheme::serializedName).sorted().toList());
        lines.add("Detected archetypes: " + profile.archetypes().stream().map(RecipeArchetype::serializedName).sorted().toList());
        profile.dimension().ifPresent(dimension -> lines.add("Dimension association: " + dimension.location()));
        lines.add("Evidence:");
        profile.evidence().forEach(value -> lines.add("- " + value));
        signature.ifPresent(value -> lines.add(ingredientLine("Signature material", value)));
        theme.ifPresent(value -> lines.add(ingredientLine("Theme ingredient", value)));
        core.ifPresent(value -> lines.add(ingredientLine("Core", value)));
        catalyst.ifPresent(value -> lines.add(ingredientLine("Catalyst", value)));
        warnings.forEach(value -> lines.add("Warning: " + value));
        return List.copyOf(lines);
    }

    private static String ingredientLine(String role, ResolvedIngredient ingredient) {
        return role + ": " + ingredient.source() + " -> " + ingredient.selectedItem();
    }
}
