package com.cappleapple.instancednotinfinite.recipe;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

final class PortalRecipeAnalysisPlanner {
    private PortalRecipeAnalysisPlanner() {
    }

    static <T> Set<T> automaticAnalysisTargets(
        Collection<T> targets,
        Set<T> predefinedTargets,
        Set<T> explicitOverrides,
        Set<T> excludedTargets,
        Set<T> poolOnlyTargets,
        Set<T> activePoolMembers,
        boolean automaticGenerationEnabled
    ) {
        if (!automaticGenerationEnabled) return Set.of();
        Set<T> result = new HashSet<>();
        for (T target : targets) {
            if (predefinedTargets.contains(target)) continue;
            boolean eligible = explicitOverrides.contains(target) || !excludedTargets.contains(target);
            if (!eligible) continue;
            if (!poolOnlyTargets.contains(target) || activePoolMembers.contains(target)) {
                result.add(target);
            }
        }
        return Set.copyOf(result);
    }
}
