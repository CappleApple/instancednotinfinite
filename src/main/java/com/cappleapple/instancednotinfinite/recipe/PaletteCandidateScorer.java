package com.cappleapple.instancednotinfinite.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

public final class PaletteCandidateScorer {
    private static final Set<String> EXCLUDED_PATHS = Set.of(
        "air", "cave_air", "void_air", "water", "lava", "bedrock", "barrier", "light",
        "structure_block", "structure_void", "jigsaw", "spawner", "trial_spawner", "vault",
        "command_block", "chain_command_block", "repeating_command_block", "end_portal", "end_gateway",
        "nether_portal", "moving_piston", "piston_head"
    );
    private static final Set<String> GENERIC_TOKENS = Set.of(
        "stone", "cobblestone", "dirt", "grass", "planks", "log", "wood", "sand", "gravel",
        "brick", "bricks", "slab", "stairs", "wall"
    );

    private PaletteCandidateScorer() {
    }

    public static List<MaterialCandidate> rank(
        ResourceLocation structureId,
        Map<ResourceLocation, Long> blockFrequencies,
        Map<ResourceLocation, ResourceLocation> obtainableItemForms
    ) {
        return rank(structureId, blockFrequencies, obtainableItemForms, ignored -> false);
    }

    public static List<MaterialCandidate> rank(
        ResourceLocation structureId,
        Map<ResourceLocation, Long> blockFrequencies,
        Map<ResourceLocation, ResourceLocation> obtainableItemForms,
        Predicate<ResourceLocation> configuredExclusion
    ) {
        long maximum = blockFrequencies.values().stream().mapToLong(Long::longValue).max().orElse(1L);
        List<MaterialCandidate> candidates = new ArrayList<>();
        blockFrequencies.forEach((blockId, occurrences) -> {
            ResourceLocation itemId = obtainableItemForms.get(blockId);
            if (itemId == null || excluded(blockId) || configuredExclusion.test(blockId)) return;
            double score = candidateScore(
                structureId.getNamespace(), blockId.getNamespace(), blockId.getPath(), occurrences, maximum);
            String reason = "%d palette blocks; %s material".formatted(
                occurrences, blockId.getNamespace().equals("minecraft") ? "vanilla" : "mod-specific");
            candidates.add(new MaterialCandidate(itemId, occurrences, score, reason));
        });
        return candidates.stream()
            .sorted(Comparator.comparingDouble(MaterialCandidate::score).reversed()
                .thenComparingInt(candidate -> DeterministicIngredientSelector.stableHash(
                    structureId + "\u0000signature\u0000" + candidate.itemId()))
                .thenComparing(MaterialCandidate::itemId))
            .limit(16)
            .toList();
    }

    public static boolean excluded(ResourceLocation blockId) {
        return excludedPath(blockId.getPath());
    }

    public static boolean excludedPath(String rawPath) {
        String path = rawPath.toLowerCase(Locale.ROOT);
        if (EXCLUDED_PATHS.contains(path)) return true;
        return path.contains("command_block") || path.contains("structure_block") || path.endsWith("_spawner")
            || path.contains("debug") || path.contains("placeholder");
    }

    public static double candidateScore(
        String structureNamespace,
        String blockNamespace,
        String blockPath,
        long occurrences,
        long maximumOccurrences
    ) {
        double frequency = Math.log1p(occurrences) / Math.log1p(Math.max(1L, maximumOccurrences));
        Set<String> tokens = new HashSet<>(SemanticThemeInference.tokens(blockPath));
        double score = frequency * 4.0D;
        if (!blockNamespace.equals("minecraft")) score += 3.0D;
        if (blockNamespace.equals(structureNamespace) && !blockNamespace.equals("minecraft")) score += 1.5D;
        score -= tokens.stream().filter(GENERIC_TOKENS::contains).count() * 1.25D;
        if (tokens.stream().anyMatch(token -> token.equals("chiseled") || token.equals("polished")
            || token.equals("decorative") || token.equals("tiles"))) score += 0.75D;
        return score;
    }
}
