package com.cappleapple.instancednotinfinite.definition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DungeonOverrideParser {
    private DungeonOverrideParser() {
    }

    public static Result parse(List<? extends String> entries) {
        Map<String, DungeonOverride> overrides = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            String entry = entries.get(index);
            try {
                Parsed parsed = parseEntry(entry);
                if (overrides.put(parsed.structureId(), parsed.override()) != null) {
                    diagnostics.add("Override entry " + index + " replaces an earlier override for " + parsed.structureId());
                }
            } catch (IllegalArgumentException exception) {
                diagnostics.add("Invalid override entry " + index + ": " + exception.getMessage());
            }
        }
        return new Result(Map.copyOf(overrides), List.copyOf(diagnostics));
    }

    private static Parsed parseEntry(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("entry is blank");
        }
        String[] pieces = raw.split(";");
        String structureId = pieces[0].trim();
        if (!DefinitionParser.isResourceId(structureId)) {
            throw new IllegalArgumentException("invalid structure ID '" + structureId + "'");
        }

        EnvironmentType environment = null;
        String customStrategy = null;
        List<BiomeRule> biomes = null;
        Integer horizontalPadding = null;
        Integer verticalPadding = null;
        Integer maximumRadius = null;
        Integer weight = null;
        PlacementMode placement = null;
        Boolean naturalSpawning = null;
        ReentryPolicy reentry = null;
        String costTier = null;
        String recipeSignature = null;
        String recipeTheme = null;
        String recipeCore = null;
        String recipeCatalyst = null;
        for (int fieldIndex = 1; fieldIndex < pieces.length; fieldIndex++) {
            String field = pieces[fieldIndex].trim();
            if (field.isEmpty()) {
                continue;
            }
            int separator = field.indexOf('=');
            if (separator < 1 || separator == field.length() - 1) {
                throw new IllegalArgumentException("field '" + field + "' must be key=value");
            }
            String key = field.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = field.substring(separator + 1).trim();
            switch (key) {
                case "environment" -> environment = enumValue(EnvironmentType.class, value, key);
                case "custom_strategy" -> customStrategy = resourceId(value, key);
                case "biomes" -> biomes = biomeRules(value);
                case "horizontal_padding" -> horizontalPadding = integer(value, key, 0, 512);
                case "vertical_padding" -> verticalPadding = integer(value, key, 0, 256);
                case "maximum_radius" -> maximumRadius = integer(value, key, 16, 1024);
                case "weight" -> weight = integer(value, key, 1, Integer.MAX_VALUE);
                case "placement" -> placement = enumValue(PlacementMode.class, value, key);
                case "natural_mob_spawning" -> naturalSpawning = bool(value, key);
                case "reentry" -> reentry = enumValue(ReentryPolicy.class, value, key);
                case "cost_tier" -> costTier = resourceId(value, key);
                case "recipe_signature" -> recipeSignature = ingredientReference(value, key);
                case "recipe_theme" -> recipeTheme = ingredientReference(value, key);
                case "recipe_core" -> recipeCore = ingredientReference(value, key);
                case "recipe_catalyst" -> recipeCatalyst = ingredientReference(value, key);
                default -> throw new IllegalArgumentException("unknown field '" + key + "'");
            }
        }
        return new Parsed(structureId, new DungeonOverride(
            environment, customStrategy, biomes, horizontalPadding, verticalPadding, maximumRadius,
            weight, placement, naturalSpawning, reentry, costTier,
            recipeSignature, recipeTheme, recipeCore, recipeCatalyst));
    }

    private static String resourceId(String value, String key) {
        if (!DefinitionParser.isResourceId(value)) {
            throw new IllegalArgumentException(key + " must be a resource ID");
        }
        return value;
    }

    private static String ingredientReference(String value, String key) {
        String id = value.startsWith("#") ? value.substring(1) : value;
        if (!DefinitionParser.isResourceId(id)) {
            throw new IllegalArgumentException(key + " must be an item ID or #item_tag");
        }
        return value;
    }

    private static List<BiomeRule> biomeRules(String value) {
        List<BiomeRule> rules = new ArrayList<>();
        for (String rawBiome : value.split(",")) {
            String reference = rawBiome.trim();
            String id = reference.startsWith("#") ? reference.substring(1) : reference;
            if (!DefinitionParser.isResourceId(id)) {
                throw new IllegalArgumentException("invalid biome reference '" + reference + "'");
            }
            rules.add(new BiomeRule(reference, 1));
        }
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("biomes must contain at least one ID or tag");
        }
        return List.copyOf(rules);
    }

    private static int integer(String value, String key, int minimum, int maximum) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) {
                throw new IllegalArgumentException(key + " must be between " + minimum + " and " + maximum);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static boolean bool(String value, String key) {
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException(key + " must be true or false");
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String key) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown " + key + " value '" + value + "'", exception);
        }
    }

    public record Result(Map<String, DungeonOverride> overrides, List<String> diagnostics) {
    }

    private record Parsed(String structureId, DungeonOverride override) {
    }
}
