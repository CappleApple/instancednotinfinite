package com.cappleapple.instancednotinfinite.definition;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

public final class BiomeSelector {
    private BiomeSelector() {
    }

    public static Selection select(RegistryAccess access, DungeonDefinition definition, long seed) throws ResolutionException {
        Registry<Biome> registry = access.registryOrThrow(Registries.BIOME);
        Map<ResourceKey<Biome>, Candidate> candidates = new LinkedHashMap<>();
        for (BiomeRule rule : definition.biomes()) {
            ResourceLocation id = ResourceLocation.tryParse(rule.id());
            if (id == null) {
                throw new ResolutionException("Invalid biome reference " + rule.reference());
            }
            if (rule.tag()) {
                TagKey<Biome> tag = TagKey.create(Registries.BIOME, id);
                List<Holder<Biome>> values = registry.getTag(tag)
                    .map(set -> set.stream().map(holder -> (Holder<Biome>)holder).toList())
                    .orElseThrow(() -> new ResolutionException("Missing or empty biome tag #" + id));
                if (values.isEmpty()) {
                    throw new ResolutionException("Missing or empty biome tag #" + id);
                }
                values.forEach(holder -> addCandidate(candidates, holder, rule.weight()));
            } else {
                ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
                Holder<Biome> holder = registry.getHolder(key)
                    .orElseThrow(() -> new ResolutionException("Unknown biome " + id));
                addCandidate(candidates, holder, rule.weight());
            }
        }
        if (candidates.isEmpty()) {
            throw new ResolutionException("No biome candidates resolved for " + definition.id());
        }

        List<Map.Entry<ResourceKey<Biome>, Candidate>> ordered = candidates.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(key -> key.location().toString())))
            .toList();
        long total = ordered.stream().mapToLong(entry -> entry.getValue().weight()).sum();
        long target = Math.floorMod(RandomSource.create(seed ^ 0x49E2A78B4D1C3F09L).nextLong(), total);
        for (Map.Entry<ResourceKey<Biome>, Candidate> entry : ordered) {
            target -= entry.getValue().weight();
            if (target < 0L) {
                return new Selection(entry.getValue().holder(), entry.getKey().location());
            }
        }
        throw new IllegalStateException("weighted biome selection exhausted unexpectedly");
    }

    private static void addCandidate(Map<ResourceKey<Biome>, Candidate> candidates, Holder<Biome> holder, int weight) {
        ResourceKey<Biome> key = holder.unwrapKey().orElseThrow(() -> new IllegalStateException("Biome holder has no registry key"));
        candidates.merge(key, new Candidate(holder, weight),
            (existing, incoming) -> new Candidate(existing.holder(), Math.max(existing.weight(), incoming.weight())));
    }

    public record Selection(Holder<Biome> holder, ResourceLocation id) {
    }

    private record Candidate(Holder<Biome> holder, int weight) {
    }
}
