package com.cappleapple.instancednotinfinite.recipe;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public final class PortalRecipeTierResolver {
    private PortalRecipeTierResolver() {
    }

    public static Optional<PortalRecipeTier> resolve(
        StructureRecipeProfile profile,
        List<PortalRecipeTier> tiers,
        ResourceLocation forcedTier
    ) {
        if (forcedTier != null) {
            Optional<PortalRecipeTier> forced = tiers.stream().filter(tier -> tier.id().equals(forcedTier)).findFirst();
            if (forced.isPresent()) return forced;
        }
        return TierSelection.select(
            profile.rarityScore(),
            profile.themes().stream().map(RecipeTheme::serializedName).collect(java.util.stream.Collectors.toSet()),
            profile.archetypes().stream().map(RecipeArchetype::serializedName).collect(java.util.stream.Collectors.toSet()),
            tiers.stream().map(tier -> new TierSelection.Candidate<>(
                tier, tier.id().toString(), tier.priority(), tier.rarityMin(), tier.rarityMax(),
                tier.requiredThemes().stream().map(RecipeTheme::serializedName).collect(java.util.stream.Collectors.toSet()),
                tier.requiredArchetypes().stream().map(RecipeArchetype::serializedName).collect(java.util.stream.Collectors.toSet())))
                .toList());
    }
}
