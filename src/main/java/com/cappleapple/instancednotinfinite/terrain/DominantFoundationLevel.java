package com.cappleapple.instancednotinfinite.terrain;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

/** Selects the lowest substantial solid layer while ignoring sparse hanging geometry. */
public final class DominantFoundationLevel {
    private static final int MINIMUM_SUBSTANTIAL_COVERAGE = 32;

    private DominantFoundationLevel() {
    }

    public static OptionalInt infer(Map<Integer, Integer> solidCoverageByY) {
        return inferSpan(solidCoverageByY).map(span -> OptionalInt.of(span.baseY())).orElseGet(OptionalInt::empty);
    }

    public static Optional<FoundationSpan> inferSpan(Map<Integer, Integer> solidCoverageByY) {
        int maximumCoverage = solidCoverageByY.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (maximumCoverage < MINIMUM_SUBSTANTIAL_COVERAGE) {
            return Optional.empty();
        }

        TreeMap<Integer, Integer> sorted = new TreeMap<>(solidCoverageByY);
        int substantialThreshold = Math.max(MINIMUM_SUBSTANTIAL_COVERAGE, (maximumCoverage + 9) / 10);
        int baseY = sorted.entrySet().stream()
            .filter(entry -> entry.getValue() >= substantialThreshold)
            .mapToInt(Map.Entry::getKey)
            .min()
            .orElseThrow();

        int plateauThreshold = Math.max(MINIMUM_SUBSTANTIAL_COVERAGE, (maximumCoverage + 1) / 2);
        int plateauStartY = sorted.tailMap(baseY, true).entrySet().stream()
            .filter(entry -> entry.getValue() >= plateauThreshold)
            .mapToInt(Map.Entry::getKey)
            .findFirst()
            .orElse(baseY);
        int topY = plateauStartY;
        while (sorted.getOrDefault(topY + 1, 0) >= plateauThreshold) {
            topY++;
        }
        return Optional.of(new FoundationSpan(baseY, topY));
    }

    public record FoundationSpan(int baseY, int topY) {
    }
}
