package com.cappleapple.instancednotinfinite.recipe;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Exact structures and structure tags denied only the automatic recipe path. */
public record RecipeTargetExclusions(Set<ResourceLocation> dungeonIds, Set<ResourceLocation> poolIds) {
    public RecipeTargetExclusions {
        dungeonIds = Set.copyOf(dungeonIds);
        poolIds = Set.copyOf(poolIds);
    }

    public static RecipeTargetExclusions configured(RegistryAccess registries) {
        Registry<Structure> structures = registries.registryOrThrow(Registries.STRUCTURE);
        Set<ResourceLocation> dungeons = new LinkedHashSet<>();
        Set<ResourceLocation> pools = new LinkedHashSet<>();
        for (String raw : configuredList(ServerConfig.INSTANCE.excludedAutomaticRecipeTargets)) {
            String value = raw == null ? "" : raw.trim();
            boolean tagEntry = value.startsWith("#");
            String idText = tagEntry ? value.substring(1) : value;
            ResourceLocation id = ResourceLocation.tryParse(idText);
            if (id == null) {
                InstancedNotInfinite.LOGGER.warn("Automatic recipe target exclusion '{}' is not a resource ID", raw);
                continue;
            }
            if (!tagEntry) {
                dungeons.add(id);
                continue;
            }
            pools.add(id);
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, id);
            var holders = structures.getTag(tag);
            if (holders.isEmpty()) {
                InstancedNotInfinite.LOGGER.warn("Automatic recipe target exclusion tag #{} is missing or empty", id);
                continue;
            }
            holders.get().stream().map(Holder::unwrapKey).flatMap(java.util.Optional::stream)
                .map(ResourceKey::location).forEach(dungeons::add);
        }
        return new RecipeTargetExclusions(dungeons, pools);
    }

    public boolean excludesDungeon(ResourceLocation dungeonId) {
        return dungeonIds.contains(dungeonId);
    }

    public boolean excludesPool(ResourceLocation poolId) {
        return poolIds.contains(poolId);
    }

    private static List<String> configuredList(ModConfigSpec.ConfigValue<List<? extends String>> value) {
        try {
            return List.copyOf(value.get());
        } catch (IllegalStateException notLoadedYet) {
            return List.copyOf(value.getDefault());
        }
    }
}
