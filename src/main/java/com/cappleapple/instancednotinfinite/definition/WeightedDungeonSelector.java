package com.cappleapple.instancednotinfinite.definition;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

final class WeightedDungeonSelector {
    private WeightedDungeonSelector() {
    }

    static <K extends Comparable<? super K>> Optional<K> select(Map<K, DungeonDefinition> definitions, long seed) {
        long totalWeight = definitions.values().stream().mapToLong(DungeonDefinition::weight).sum();
        if (totalWeight <= 0L) {
            return Optional.empty();
        }
        long slot = Math.floorMod(seed, totalWeight);
        var entries = definitions.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
            .toList();
        for (Map.Entry<K, DungeonDefinition> entry : entries) {
            if (slot < entry.getValue().weight()) {
                return Optional.of(entry.getKey());
            }
            slot -= entry.getValue().weight();
        }
        throw new IllegalStateException("Weighted dungeon selection exceeded its validated total");
    }
}
