package com.cappleapple.instancednotinfinite.recipe;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Dependency-free tier ordering used by both production resolution and pure unit tests. */
public final class TierSelection {
    private TierSelection() {
    }

    public static <T> Optional<T> select(
        double rarity,
        Set<String> themes,
        Set<String> archetypes,
        List<Candidate<T>> candidates
    ) {
        return candidates.stream()
            .filter(candidate -> rarity >= candidate.rarityMin() && rarity <= candidate.rarityMax())
            .filter(candidate -> themes.containsAll(candidate.requiredThemes()))
            .filter(candidate -> archetypes.containsAll(candidate.requiredArchetypes()))
            .sorted(Comparator.<Candidate<T>>comparingInt(Candidate::priority).reversed()
                .thenComparingDouble(candidate -> candidate.rarityMax() - candidate.rarityMin())
                .thenComparing(Candidate::stableId))
            .map(Candidate::value)
            .findFirst();
    }

    public record Candidate<T>(
        T value,
        String stableId,
        int priority,
        double rarityMin,
        double rarityMax,
        Set<String> requiredThemes,
        Set<String> requiredArchetypes
    ) {
        public Candidate {
            requiredThemes = Set.copyOf(requiredThemes);
            requiredArchetypes = Set.copyOf(requiredArchetypes);
        }
    }
}
