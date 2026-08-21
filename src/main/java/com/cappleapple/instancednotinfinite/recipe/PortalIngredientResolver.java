package com.cappleapple.instancednotinfinite.recipe;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

public final class PortalIngredientResolver {
    private PortalIngredientResolver() {
    }

    public static Optional<ResolvedIngredient> resolve(
        ResourceLocation structureId,
        String role,
        IngredientReference reference,
        ItemTagLookup tags
    ) {
        return resolve(structureId, role, reference, tags, ignored -> false);
    }

    public static Optional<ResolvedIngredient> resolve(
        ResourceLocation structureId,
        String role,
        IngredientReference reference,
        ItemTagLookup tags,
        Predicate<ResourceLocation> excludedItem
    ) {
        List<ResourceLocation> candidates;
        if (reference.tag()) {
            candidates = tags.items(reference.id()).stream().filter(PortalIngredientResolver::validItem)
                .filter(id -> !excludedItem.test(id)).distinct().sorted().toList();
        } else {
            candidates = validItem(reference.id()) && !excludedItem.test(reference.id()) ? List.of(reference.id()) : List.of();
        }
        if (candidates.isEmpty()) return Optional.empty();
        ResourceLocation selected = DeterministicIngredientSelector.select(structureId, role, candidates);
        return Optional.of(new ResolvedIngredient(reference, selected, Ingredient.of(BuiltInRegistries.ITEM.get(selected))));
    }

    private static boolean validItem(ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id) != Items.AIR;
    }
}
