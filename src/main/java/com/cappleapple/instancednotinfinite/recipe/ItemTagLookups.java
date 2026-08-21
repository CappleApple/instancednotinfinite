package com.cappleapple.instancednotinfinite.recipe;

import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.conditions.ICondition;

public final class ItemTagLookups {
    private ItemTagLookups() {
    }

    public static ItemTagLookup staged(ICondition.IContext context) {
        return tagId -> context.getTag(TagKey.create(Registries.ITEM, tagId)).stream()
            .map(holder -> BuiltInRegistries.ITEM.getKey(holder.value())).filter(java.util.Objects::nonNull).sorted().toList();
    }

    public static ItemTagLookup bound(RegistryAccess ignored) {
        return tagId -> BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tagId))
            .stream().flatMap(set -> set.stream())
            .map(holder -> BuiltInRegistries.ITEM.getKey(holder.value())).filter(java.util.Objects::nonNull).sorted().toList();
    }
}
