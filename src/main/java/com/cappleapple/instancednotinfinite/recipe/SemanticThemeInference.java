package com.cappleapple.instancednotinfinite.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Shared token table for low-priority names, biome tags/IDs, and palette IDs. */
public final class SemanticThemeInference {
    private static final Pattern SEPARATORS = Pattern.compile("[^a-z0-9]+");
    private static final Map<String, Signal> SIGNALS = buildSignals();

    private SemanticThemeInference() {
    }

    public static ThemeInferenceResult fromStructureName(String path) {
        return infer(path, "structure ID");
    }

    public static ThemeInferenceResult fromSemanticText(String text, String evidenceSource) {
        return infer(text, evidenceSource);
    }

    public static Set<String> tokens(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return Set.copyOf(Arrays.stream(SEPARATORS.split(normalized)).filter(token -> !token.isBlank()).toList());
    }

    private static ThemeInferenceResult infer(String text, String source) {
        EnumSet<RecipeTheme> themes = EnumSet.noneOf(RecipeTheme.class);
        EnumSet<RecipeArchetype> archetypes = EnumSet.noneOf(RecipeArchetype.class);
        List<String> evidence = new ArrayList<>();
        tokens(text).stream().sorted().forEach(token -> {
            Signal signal = SIGNALS.get(token);
            if (signal == null) return;
            themes.addAll(signal.themes());
            archetypes.addAll(signal.archetypes());
            evidence.add(source + " token '" + token + "'");
        });
        return new ThemeInferenceResult(themes, archetypes, evidence);
    }

    private static Map<String, Signal> buildSignals() {
        Map<String, Signal> values = new LinkedHashMap<>();
        add(values, List.of("burning", "flame", "flames", "fiery", "fire", "blazing"),
            themes(RecipeTheme.FIRE, RecipeTheme.HOT));
        add(values, List.of("frozen", "ice", "icy", "glacier", "cold"), themes(RecipeTheme.COLD));
        add(values, List.of("snow", "snowy"), themes(RecipeTheme.COLD, RecipeTheme.SNOW));
        add(values, List.of("sunken", "ocean", "underwater", "aquatic", "prismarine"),
            themes(RecipeTheme.OCEAN, RecipeTheme.WATER));
        add(values, List.of("water", "river", "lake"), themes(RecipeTheme.WATER));
        add(values, List.of("desert", "sand", "sandstone", "badlands"), themes(RecipeTheme.DESERT, RecipeTheme.HOT));
        add(values, List.of("jungle", "tropical"), themes(RecipeTheme.JUNGLE, RecipeTheme.FOREST));
        add(values, List.of("forest", "woodland", "grove", "taiga"), themes(RecipeTheme.FOREST));
        add(values, List.of("swamp", "marsh", "mangrove"), themes(RecipeTheme.SWAMP, RecipeTheme.WATER));
        add(values, List.of("nether", "infernal", "blackstone", "soul"), themes(RecipeTheme.NETHER, RecipeTheme.HOT));
        add(values, List.of("end", "ender", "chorus", "purpur"), themes(RecipeTheme.END));
        add(values, List.of("cave", "cavern", "caverns", "deep", "underground", "deepslate"),
            themes(RecipeTheme.CAVE, RecipeTheme.UNDERGROUND));
        add(values, List.of("mountain", "mountains", "peak", "peaks", "cliff"), themes(RecipeTheme.MOUNTAIN));
        add(values, List.of("crypt", "grave", "tomb", "mausoleum", "undead", "skeletal"),
            themesAndArchetypes(themes(RecipeTheme.UNDEAD), archetypes(RecipeArchetype.CRYPT)));
        add(values, List.of("cursed", "arcane", "magic", "enchanted"), themes(RecipeTheme.MAGIC));
        add(values, List.of("arena", "colosseum"),
            themesAndArchetypes(themes(RecipeTheme.COMBAT), archetypes(RecipeArchetype.ARENA, RecipeArchetype.BOSS)));
        add(values, List.of("boss"), archetypes(RecipeArchetype.BOSS));
        add(values, List.of("citadel", "fortress", "bastion", "stronghold", "castle"),
            themesAndArchetypes(themes(RecipeTheme.FORTRESS, RecipeTheme.COMBAT), archetypes(RecipeArchetype.FORTRESS)));
        add(values, List.of("temple", "shrine", "sanctum"),
            themesAndArchetypes(themes(RecipeTheme.TEMPLE, RecipeTheme.ANCIENT), archetypes(RecipeArchetype.TEMPLE)));
        add(values, List.of("ruined", "ruin", "ruins", "remnant"),
            themesAndArchetypes(themes(RecipeTheme.RUINS), archetypes(RecipeArchetype.RUIN)));
        add(values, List.of("ancient", "old"), themes(RecipeTheme.ANCIENT));
        add(values, List.of("factory", "industrial", "workshop"),
            themesAndArchetypes(themes(RecipeTheme.INDUSTRIAL), archetypes(RecipeArchetype.INDUSTRIAL)));
        add(values, List.of("mine", "mineshaft", "quarry"),
            themesAndArchetypes(themes(RecipeTheme.UNDERGROUND, RecipeTheme.INDUSTRIAL), archetypes(RecipeArchetype.MINE)));
        add(values, List.of("village", "settlement", "town"), archetypes(RecipeArchetype.SETTLEMENT));
        return Map.copyOf(values);
    }

    private static void add(Map<String, Signal> target, List<String> tokens, Signal signal) {
        tokens.forEach(token -> target.put(token, signal));
    }

    private static Signal themes(RecipeTheme... themes) {
        return new Signal(Set.of(themes), Set.of());
    }

    private static Signal archetypes(RecipeArchetype... archetypes) {
        return new Signal(Set.of(), Set.of(archetypes));
    }

    private static Signal themesAndArchetypes(Signal themes, Signal archetypes) {
        return new Signal(themes.themes(), archetypes.archetypes());
    }

    private record Signal(Set<RecipeTheme> themes, Set<RecipeArchetype> archetypes) {
    }
}
