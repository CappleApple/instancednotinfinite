package com.cappleapple.instancednotinfinite.recipe;

import java.util.List;
import java.util.Set;

public record ThemeInferenceResult(
    Set<RecipeTheme> themes,
    Set<RecipeArchetype> archetypes,
    List<String> evidence
) {
    public ThemeInferenceResult {
        themes = Set.copyOf(themes);
        archetypes = Set.copyOf(archetypes);
        evidence = List.copyOf(evidence);
    }
}
