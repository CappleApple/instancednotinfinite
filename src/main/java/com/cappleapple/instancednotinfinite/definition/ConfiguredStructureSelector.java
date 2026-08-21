package com.cappleapple.instancednotinfinite.definition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ConfiguredStructureSelector {
    private ConfiguredStructureSelector() {
    }

    public static Result resolve(
        List<? extends String> directIds,
        List<? extends String> tagIds,
        List<? extends String> exclusions,
        Predicate<String> structureExists,
        Function<String, Optional<? extends Collection<String>>> tagResolver
    ) {
        Set<String> candidates = new LinkedHashSet<>();
        Map<String, Set<String>> sources = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();

        for (String raw : directIds) {
            String id = normalize(raw);
            if (!DefinitionParser.isResourceId(id)) {
                diagnostics.add("Invalid configured structure ID '" + raw + "'");
            } else if (!structureExists.test(id)) {
                diagnostics.add("Unknown configured structure " + id);
            } else {
                candidates.add(id);
                sources.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add("direct");
            }
        }

        for (String raw : tagIds) {
            String tag = normalizeTag(raw);
            if (!DefinitionParser.isResourceId(tag)) {
                diagnostics.add("Invalid configured structure tag '" + raw + "'");
                continue;
            }
            Optional<? extends Collection<String>> resolved = tagResolver.apply(tag);
            if (resolved.isEmpty() || resolved.get().isEmpty()) {
                diagnostics.add("Missing or empty configured structure tag #" + tag);
                continue;
            }
            for (String id : resolved.get()) {
                if (structureExists.test(id)) {
                    candidates.add(id);
                    sources.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add("tag #" + tag);
                }
            }
        }

        for (String raw : exclusions) {
            String id = normalize(raw);
            if (!DefinitionParser.isResourceId(id)) {
                diagnostics.add("Invalid excluded structure ID '" + raw + "'");
                continue;
            }
            candidates.remove(id);
            sources.remove(id);
        }

        List<String> ordered = candidates.stream().sorted().toList();
        Map<String, List<String>> immutableSources = new LinkedHashMap<>();
        ordered.forEach(id -> immutableSources.put(id, List.copyOf(sources.getOrDefault(id, Set.of()))));
        return new Result(ordered, Map.copyOf(immutableSources), List.copyOf(diagnostics));
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String normalizeTag(String raw) {
        String value = normalize(raw);
        return value.startsWith("#") ? value.substring(1) : value;
    }

    public record Result(List<String> structureIds, Map<String, List<String>> sources, List<String> diagnostics) {
    }
}
