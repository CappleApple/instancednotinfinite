package com.cappleapple.instancednotinfinite.definition;

import com.cappleapple.instancednotinfinite.manifestation.PortalColor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class DefinitionParser {
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    private DefinitionParser() {
    }

    public static DungeonDefinition parse(String id, JsonElement element) throws DefinitionException {
        return parse(id, element, 48, 32);
    }

    public static DungeonDefinition parse(
        String id,
        JsonElement element,
        int defaultHorizontalPadding,
        int defaultVerticalPadding
    ) throws DefinitionException {
        try {
            if (!isResourceId(id)) {
                throw field(id, "id", "invalid resource location");
            }
            if (!element.isJsonObject()) {
                throw field(id, "$", "definition root must be a JSON object");
            }
            JsonObject root = element.getAsJsonObject();
            int format = integer(root, "formatVersion", true, 1);
            String structure = string(root, "structure", true, null);
            if (!isResourceId(structure)) {
                throw field(id, "structure", "invalid structure id: " + structure);
            }
            StructureKind kind = enumValue(root, "structureKind", StructureKind.class, StructureKind.AUTO);
            int weight = integer(root, "weight", false, 1);
            List<BiomeRule> biomes = parseBiomes(id, root.get("biomes"));

            JsonObject heightJson = object(root, "height", false);
            HeightContext height = heightJson == null
                ? new HeightContext(48, 80)
                : new HeightContext(integer(heightJson, "min", false, 48), integer(heightJson, "max", false, 80));

            JsonObject environmentJson = object(root, "environment", true);
            EnvironmentType environment = enumValue(environmentJson, "type", EnvironmentType.class, null);
            String customEnvironment = string(environmentJson, "customStrategy", false, null);

            JsonObject terrainJson = object(root, "terrain", false);
            TerrainSettings terrain = terrainJson == null
                ? new TerrainSettings(defaultHorizontalPadding, defaultVerticalPadding, 192)
                : new TerrainSettings(
                    integer(terrainJson, "horizontalPadding", false, defaultHorizontalPadding),
                    integer(terrainJson, "verticalPadding", false, defaultVerticalPadding),
                    integer(terrainJson, "maximumRadius", false, 192));

            JsonObject portalJson = object(root, "portal", false);
            PortalSettings portal = portalJson == null
                ? PortalSettings.DEFAULT
                : new PortalSettings(
                    portalColor(id, portalJson, "innerColor"),
                    portalColor(id, portalJson, "outerColor"));

            JsonObject entryJson = object(root, "entry", false);
            EntryPoint entry = entryJson == null
                ? new EntryPoint(0, 1, 0, 0.0F, 0.0F)
                : new EntryPoint(
                    integer(entryJson, "x", false, 0),
                    integer(entryJson, "y", false, 1),
                    integer(entryJson, "z", false, 0),
                    decimal(entryJson, "yaw", 0.0F),
                    decimal(entryJson, "pitch", 0.0F));

            JsonObject placementJson = object(root, "placement", false);
            PlacementMode placement = placementJson == null
                ? PlacementMode.DIRECT
                : enumValue(placementJson, "mode", PlacementMode.class, PlacementMode.DIRECT);
            JsonObject decorationJson = object(root, "decoration", false);
            DecorationMode decoration = decorationJson == null
                ? DecorationMode.SAFE
                : enumValue(decorationJson, "mode", DecorationMode.class, DecorationMode.SAFE);
            boolean naturalSpawning = bool(root, "allowNaturalMobSpawning", true);
            ReentryPolicy reentry = enumValue(root, "reentry", ReentryPolicy.class, ReentryPolicy.WHILE_ACTIVE);

            try {
                return new DungeonDefinition(
                    id, format, structure, kind, weight, biomes, height, environment, customEnvironment,
                    terrain, portal, entry, placement, decoration, naturalSpawning, reentry);
            } catch (IllegalArgumentException exception) {
                throw field(id, inferField(exception.getMessage()), exception.getMessage(), exception);
            }
        } catch (DefinitionException exception) {
            if ("<unknown>".equals(exception.definitionId())) {
                throw field(id, exception.field(), exception.getMessage(), exception);
            }
            throw exception;
        } catch (JsonParseException | IllegalStateException | NumberFormatException exception) {
            throw field(id, "$", "malformed JSON value: " + exception.getMessage(), exception);
        }
    }

    public static boolean isResourceId(String value) {
        return value != null && RESOURCE_ID.matcher(value).matches();
    }

    private static List<BiomeRule> parseBiomes(String id, JsonElement element) throws DefinitionException {
        if (element == null || !element.isJsonArray()) {
            throw field(id, "biomes", "must be a JSON array");
        }
        JsonArray array = element.getAsJsonArray();
        List<BiomeRule> rules = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonElement entry = array.get(index);
            String reference;
            int weight;
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                reference = entry.getAsString();
                weight = 1;
            } else if (entry.isJsonObject()) {
                JsonObject object = entry.getAsJsonObject();
                reference = string(object, "id", true, null);
                weight = integer(object, "weight", false, 1);
            } else {
                throw field(id, "biomes[" + index + "]", "must be a string or object");
            }
            String rawId = reference.startsWith("#") ? reference.substring(1) : reference;
            if (!isResourceId(rawId)) {
                throw field(id, "biomes[" + index + "].id", "invalid biome or tag id: " + reference);
            }
            try {
                rules.add(new BiomeRule(reference, weight));
            } catch (IllegalArgumentException exception) {
                throw field(id, "biomes[" + index + "].weight", exception.getMessage(), exception);
            }
        }
        if (rules.isEmpty()) {
            throw field(id, "biomes", "must contain at least one rule");
        }
        return rules;
    }

    private static JsonObject object(JsonObject owner, String key, boolean required) throws DefinitionException {
        JsonElement element = owner.get(key);
        if (element == null) {
            if (required) {
                throw field("<unknown>", key, "missing required object");
            }
            return null;
        }
        if (!element.isJsonObject()) {
            throw field("<unknown>", key, "must be an object");
        }
        return element.getAsJsonObject();
    }

    private static String string(JsonObject owner, String key, boolean required, String fallback) throws DefinitionException {
        JsonElement element = owner.get(key);
        if (element == null) {
            if (required) {
                throw field("<unknown>", key, "missing required string");
            }
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw field("<unknown>", key, "must be a string");
        }
        return element.getAsString();
    }

    private static int integer(JsonObject owner, String key, boolean required, int fallback) throws DefinitionException {
        JsonElement element = owner.get(key);
        if (element == null) {
            if (required) {
                throw field("<unknown>", key, "missing required integer");
            }
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw field("<unknown>", key, "must be an integer");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw field("<unknown>", key, "must be an integer");
        }
        return (int)value;
    }

    private static float decimal(JsonObject owner, String key, float fallback) throws DefinitionException {
        JsonElement element = owner.get(key);
        if (element == null) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw field("<unknown>", key, "must be a number");
        }
        float value = element.getAsFloat();
        if (!Float.isFinite(value)) {
            throw field("<unknown>", key, "must be finite");
        }
        return value;
    }

    private static boolean bool(JsonObject owner, String key, boolean fallback) throws DefinitionException {
        JsonElement element = owner.get(key);
        if (element == null) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw field("<unknown>", key, "must be a boolean");
        }
        return element.getAsBoolean();
    }

    private static String portalColor(String id, JsonObject owner, String key) throws DefinitionException {
        String value = string(owner, key, false, null);
        if (value != null && !PortalColor.isValid(value)) {
            throw field(id, "portal." + key, "must use #RRGGBBAA, including opacity in the final byte");
        }
        return value;
    }

    private static <E extends Enum<E>> E enumValue(JsonObject owner, String key, Class<E> type, E fallback) throws DefinitionException {
        String value = string(owner, key, fallback == null, fallback == null ? null : fallback.name());
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw field("<unknown>", key, "unknown value '" + value + "'", exception);
        }
    }

    private static String inferField(String message) {
        if (message == null) {
            return "$";
        }
        int separator = message.indexOf(' ');
        String candidate = separator < 0 ? message : message.substring(0, separator);
        return candidate.contains(".") ? candidate : "$";
    }

    private static DefinitionException field(String id, String field, String message) {
        return new DefinitionException(id, field, message);
    }

    private static DefinitionException field(String id, String field, String message, Throwable cause) {
        return new DefinitionException(id, field, message, cause);
    }
}
