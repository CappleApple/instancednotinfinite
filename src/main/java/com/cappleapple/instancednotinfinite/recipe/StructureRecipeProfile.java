package com.cappleapple.instancednotinfinite.recipe;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record StructureRecipeProfile(
    ResourceLocation structureId,
    double rarityScore,
    double sizeScore,
    Set<RecipeTheme> themes,
    Set<RecipeArchetype> archetypes,
    List<MaterialCandidate> signatureMaterials,
    Optional<ResourceKey<Level>> dimension,
    List<String> evidence,
    boolean paletteAnalyzed,
    boolean fallback
) {
    public StructureRecipeProfile {
        rarityScore = clamp(rarityScore);
        sizeScore = clamp(sizeScore);
        themes = Set.copyOf(themes);
        archetypes = Set.copyOf(archetypes);
        signatureMaterials = List.copyOf(signatureMaterials);
        dimension = dimension == null ? Optional.empty() : dimension;
        evidence = List.copyOf(evidence);
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
