package com.cappleapple.instancednotinfinite.definition;

import com.cappleapple.instancednotinfinite.compat.mowzie.MowzieStructureAccess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class AutomaticDungeonResolver {
    private static final ResourceLocation MOWZIE_MONASTERY = ResourceLocation.fromNamespaceAndPath("mowziesmobs", "monastery");

    private AutomaticDungeonResolver() {
    }

    public static ResolvedDungeonOption resolve(
        RegistryAccess access,
        ResourceLocation structureId,
        List<String> sources,
        DungeonOverride override,
        int defaultHorizontalPadding,
        int defaultVerticalPadding,
        int maximumRadius
    ) throws ResolutionException {
        Registry<Structure> structures = access.registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, structureId);
        Holder.Reference<Structure> structureHolder = structures.getHolder(structureKey)
            .orElseThrow(() -> new ResolutionException("Unknown worldgen structure " + structureId));
        Structure structure = structureHolder.value();
        List<Holder<Biome>> automaticBiomes = structure.biomes().stream()
            .map(holder -> (Holder<Biome>)holder)
            .filter(holder -> holder.unwrapKey().isPresent())
            .sorted(Comparator.comparing(holder -> holder.unwrapKey().orElseThrow().location().toString()))
            .toList();
        if (structure instanceof MowzieStructureAccess mowzieStructure) {
            List<Holder<Biome>> configuredBiomes = mowzieStructure.instancednotinfinite$allowedBiomes().stream()
                .filter(holder -> holder.unwrapKey().isPresent())
                .sorted(Comparator.comparing(holder -> holder.unwrapKey().orElseThrow().location().toString()))
                .toList();
            if (!configuredBiomes.isEmpty()) automaticBiomes = configuredBiomes;
        }
        if (automaticBiomes.isEmpty()) {
            throw new ResolutionException("Structure exposes no keyed allowed biomes");
        }

        List<BiomeRule> biomeRules;
        List<Holder<Biome>> effectiveBiomes;
        if (override != null && override.biomes() != null) {
            biomeRules = override.biomes();
            effectiveBiomes = resolveBiomeRules(access.registryOrThrow(Registries.BIOME), biomeRules);
        } else {
            biomeRules = automaticBiomes.stream()
                .map(holder -> new BiomeRule(holder.unwrapKey().orElseThrow().location().toString(), 1))
                .toList();
            effectiveBiomes = automaticBiomes;
        }

        String adaptation = structure.terrainAdaptation().getSerializedName();
        String generationStep = structure.step().getName();
        ResourceLocation typeId = BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type());
        String structureType = typeId == null ? structure.type().toString() : typeId.toString();
        OptionalInt absoluteStartHeight = EncodedStructureMetadata.absoluteStartHeight(access, structure);
        EnvironmentInference.Classification inferred = EnvironmentInference.classify(new EnvironmentInference.Evidence(
            allMatch(effectiveBiomes, BiomeTags.IS_NETHER),
            allMatch(effectiveBiomes, BiomeTags.IS_END),
            allMatch(effectiveBiomes, BiomeTags.IS_OCEAN),
            adaptation,
            generationStep,
            absoluteStartHeight.isPresent() ? absoluteStartHeight.getAsInt() : null));
        EnvironmentType automaticEnvironment = structureId.equals(MOWZIE_MONASTERY)
            ? EnvironmentType.SURFACE
            : inferred.environment();
        String automaticReason = structureId.equals(MOWZIE_MONASTERY)
            ? "Mowzie monastery is an authored mountain surface structure"
            : inferred.reason();
        EnvironmentType environment = override != null && override.environment() != null
            ? override.environment()
            : automaticEnvironment;
        String customStrategy = override == null ? null : override.customStrategy();
        String environmentSource = override != null && override.environment() != null ? "config override" : "automatic";
        String environmentReason = override != null && override.environment() != null
            ? "explicit per-structure override"
            : automaticReason;

        int horizontalPadding = value(override == null ? null : override.horizontalPadding(), defaultHorizontalPadding);
        int verticalPadding = value(override == null ? null : override.verticalPadding(), defaultVerticalPadding);
        int optionMaximumRadius = Math.min(value(override == null ? null : override.maximumRadius(), maximumRadius), maximumRadius);
        int weight = value(override == null ? null : override.weight(), 1);
        PlacementMode placement = override != null && override.placement() != null ? override.placement() : PlacementMode.NATURAL;
        boolean naturalSpawning = override == null || override.allowNaturalMobSpawning() == null || override.allowNaturalMobSpawning();
        ReentryPolicy reentry = override != null && override.reentry() != null ? override.reentry() : ReentryPolicy.WHILE_ACTIVE;
        HeightContext height = heightFor(environment);

        DungeonDefinition definition = new DungeonDefinition(
            structureId.toString(), 1, structureId.toString(), StructureKind.WORLDGEN, weight, biomeRules,
            height, environment, customStrategy, new TerrainSettings(horizontalPadding, verticalPadding, optionMaximumRadius),
            PortalSettings.DEFAULT, new EntryPoint(0, 1, 0, 0.0F, 0.0F), placement, DecorationMode.SAFE,
            naturalSpawning, reentry);
        AutomaticDungeonMetadata metadata = new AutomaticDungeonMetadata(
            structureId, structureId, sources, effectiveBiomes.size(), environment,
            environmentSource, environmentReason, structureType,
            adaptation, generationStep, horizontalPadding, verticalPadding, weight, placement, true);
        return new ResolvedDungeonOption(definition, metadata);
    }

    private static List<Holder<Biome>> resolveBiomeRules(Registry<Biome> registry, List<BiomeRule> rules) throws ResolutionException {
        List<Holder<Biome>> resolved = new ArrayList<>();
        for (BiomeRule rule : rules) {
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
                resolved.addAll(values);
            } else {
                ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
                resolved.add(registry.getHolder(key).orElseThrow(() -> new ResolutionException("Unknown biome " + id)));
            }
        }
        List<Holder<Biome>> deduplicated = resolved.stream()
            .filter(holder -> holder.unwrapKey().isPresent())
            .collect(java.util.stream.Collectors.toMap(
                holder -> holder.unwrapKey().orElseThrow(), holder -> holder, (left, right) -> left, java.util.LinkedHashMap::new))
            .values().stream().toList();
        if (deduplicated.isEmpty()) {
            throw new ResolutionException("Biome override resolved to no biomes");
        }
        return deduplicated;
    }

    private static boolean allMatch(List<Holder<Biome>> biomes, TagKey<Biome> tag) {
        return !biomes.isEmpty() && biomes.stream().allMatch(holder -> holder.is(tag));
    }

    private static int value(Integer override, int fallback) {
        return override == null ? fallback : override;
    }

    private static HeightContext heightFor(EnvironmentType environment) {
        return switch (environment) {
            case CAVE, UNDERGROUND -> new HeightContext(-48, 16);
            case UNDERWATER -> new HeightContext(32, 63);
            case OCEAN_SURFACE -> new HeightContext(56, 80);
            case NETHER_LIKE -> new HeightContext(32, 96);
            case END_LIKE -> new HeightContext(48, 96);
            default -> new HeightContext(64, 120);
        };
    }
}
