package com.cappleapple.instancednotinfinite.recipe;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Resolved block and block-tag denylist applied to every automatic recipe ingredient role. */
public record RecipeIngredientExclusions(Set<ResourceLocation> blockIds) {
    public RecipeIngredientExclusions {
        blockIds = Set.copyOf(blockIds);
    }

    public static RecipeIngredientExclusions configured(RegistryAccess registries) {
        Registry<net.minecraft.world.level.block.Block> blocks = registries.registryOrThrow(Registries.BLOCK);
        Set<ResourceLocation> excluded = new LinkedHashSet<>();
        for (String raw : configuredList(ServerConfig.INSTANCE.excludedRecipeBlocks)) {
            String value = raw == null ? "" : raw.trim();
            boolean tagEntry = value.startsWith("#");
            String idText = tagEntry ? value.substring(1) : value;
            ResourceLocation id = ResourceLocation.tryParse(idText);
            if (id == null) {
                InstancedNotInfinite.LOGGER.warn("Automatic recipe block exclusion '{}' is not a resource ID", raw);
                continue;
            }
            if (tagEntry) {
                TagKey<net.minecraft.world.level.block.Block> tag = TagKey.create(Registries.BLOCK, id);
                var holders = blocks.getTag(tag);
                if (holders.isEmpty()) {
                    InstancedNotInfinite.LOGGER.warn("Automatic recipe block exclusion tag #{} is missing or empty", id);
                    continue;
                }
                holders.get().stream().map(Holder::unwrapKey).flatMap(java.util.Optional::stream)
                    .map(ResourceKey::location).forEach(excluded::add);
            } else if (blocks.containsKey(id)) {
                excluded.add(id);
            } else {
                InstancedNotInfinite.LOGGER.warn("Automatic recipe block exclusion {} is not a registered block", id);
            }
        }
        return new RecipeIngredientExclusions(excluded);
    }

    public boolean excludesBlock(ResourceLocation blockId) {
        return blockIds.contains(blockId);
    }

    public boolean excludesItem(ResourceLocation itemId) {
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) return false;
        var item = BuiltInRegistries.ITEM.get(itemId);
        if (!(item instanceof BlockItem blockItem)) return false;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
        return blockId != null && blockIds.contains(blockId);
    }

    private static List<String> configuredList(ModConfigSpec.ConfigValue<List<? extends String>> value) {
        try {
            return List.copyOf(value.get());
        } catch (IllegalStateException notLoadedYet) {
            return List.copyOf(value.getDefault());
        }
    }
}
