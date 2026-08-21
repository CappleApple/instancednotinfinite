package com.cappleapple.instancednotinfinite.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class PortalRecipeTierParser {
    private PortalRecipeTierParser() {
    }

    public static Result parse(Map<ResourceLocation, JsonElement> resources) {
        Map<ResourceLocation, PortalRecipeTier> tiers = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                tiers.put(entry.getKey(), parseOne(entry.getKey(), entry.getValue()));
            } catch (RuntimeException exception) {
                diagnostics.add("Invalid portal recipe tier " + entry.getKey() + ": " + exception.getMessage());
            }
        });
        return new Result(List.copyOf(tiers.values()), List.copyOf(diagnostics));
    }

    private static PortalRecipeTier parseOne(ResourceLocation id, JsonElement element) {
        if (!element.isJsonObject()) throw new IllegalArgumentException("root must be an object");
        JsonObject object = element.getAsJsonObject();
        int priority = integer(object, "priority", 0);
        double rarityMin = decimal(object, "rarity_min", 0.0D);
        double rarityMax = decimal(object, "rarity_max", 1.0D);
        if (!object.has("core") || !object.get("core").isJsonPrimitive()) {
            throw new IllegalArgumentException("core must be an item ID or #item_tag");
        }
        IngredientReference core = IngredientReference.parse(object.get("core").getAsString());
        Set<RecipeTheme> themes = enumSet(object, "themes", RecipeTheme.class);
        Set<RecipeArchetype> archetypes = enumSet(object, "archetypes", RecipeArchetype.class);
        return new PortalRecipeTier(id, priority, rarityMin, rarityMax, core, themes, archetypes);
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static double decimal(JsonObject object, String key, double fallback) {
        return object.has(key) ? object.get(key).getAsDouble() : fallback;
    }

    private static <E extends Enum<E>> Set<E> enumSet(JsonObject object, String key, Class<E> type) {
        EnumSet<E> values = EnumSet.noneOf(type);
        if (!object.has(key)) return values;
        if (!object.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        object.getAsJsonArray(key).forEach(value -> {
            try {
                values.add(Enum.valueOf(type, value.getAsString().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("unknown " + key + " value '" + value.getAsString() + "'");
            }
        });
        return values;
    }

    public record Result(List<PortalRecipeTier> tiers, List<String> diagnostics) {
    }
}
