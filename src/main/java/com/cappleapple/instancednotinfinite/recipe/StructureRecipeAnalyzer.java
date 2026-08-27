package com.cappleapple.instancednotinfinite.recipe;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Registry/template-only analysis; it never creates chunks or physically places a structure. */
public final class StructureRecipeAnalyzer {
    private static final int MAX_POOLS = 128;
    private static final int MAX_TEMPLATES = 512;
    private static final int MAX_TEMPLATE_BLOCKS = 250_000;

    public StructureRecipeProfile analyze(
        ResourceLocation dungeonId,
        ResourceLocation sourceId,
        EnvironmentType configuredEnvironment,
        RegistryAccess registries,
        ResourceManager resources,
        StructureTemplateManager templateManager,
        RecipeInferenceSettings settings,
        RecipeIngredientExclusions exclusions
    ) {
        return analyzeWithDependencies(
            dungeonId, sourceId, configuredEnvironment, registries, resources, templateManager, settings, exclusions).profile();
    }

    public StructureRecipeAnalysis analyzeWithDependencies(
        ResourceLocation dungeonId,
        ResourceLocation sourceId,
        EnvironmentType configuredEnvironment,
        RegistryAccess registries,
        ResourceManager resources,
        StructureTemplateManager templateManager,
        RecipeInferenceSettings settings,
        RecipeIngredientExclusions exclusions
    ) {
        Set<ResourceLocation> resourceDependencies = new HashSet<>();
        resourceDependencies.add(structureResource(sourceId));
        EnumSet<RecipeTheme> themes = EnumSet.noneOf(RecipeTheme.class);
        EnumSet<RecipeArchetype> archetypes = EnumSet.noneOf(RecipeArchetype.class);
        List<String> evidence = new ArrayList<>();
        Registry<Structure> structures = registries.registryOrThrow(Registries.STRUCTURE);
        Structure structure = structures.get(sourceId);

        if (settings.nameInference()) {
            merge(SemanticThemeInference.fromStructureName(sourceId.getPath()), themes, archetypes, evidence);
        }
        applyConfiguredEnvironment(configuredEnvironment, themes, evidence);

        JsonElement encodedStructure = null;
        if (structure != null) {
            encodedStructure = Structure.DIRECT_CODEC
                .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), structure)
                .resultOrPartial(message -> InstancedNotInfinite.LOGGER.debug("Could not encode structure {} for recipe analysis: {}", sourceId, message))
                .orElse(null);
            if (settings.biomeInference()) analyzeBiomes(structure, themes, archetypes, evidence);
            if (settings.dimensionInference()) analyzeWorldgenEnvironment(structure, themes, evidence);
        }

        TemplateAnalysis templates = settings.paletteInference()
            ? analyzeTemplates(sourceId, structure != null, encodedStructure, registries, resources, templateManager,
                resourceDependencies)
            : TemplateAnalysis.empty();
        if (settings.paletteInference()) {
            if (templates.templatesAnalyzed() > 0) {
                evidence.add("palette analysis of " + templates.templatesAnalyzed() + " structure template(s)");
            } else {
                evidence.add("palette unavailable; using worldgen, biome, and semantic evidence");
            }
        }

        Map<ResourceLocation, ResourceLocation> itemForms = obtainableItemForms(templates.blockFrequencies().keySet());
        List<MaterialCandidate> candidates = PaletteCandidateScorer.rank(
            dungeonId, templates.blockFrequencies(), itemForms, exclusions::excludesBlock);
        candidates.stream().limit(24).forEach(candidate -> merge(
            SemanticThemeInference.fromSemanticText(candidate.itemId().getPath(), "palette material"),
            themes, archetypes, evidence));

        double size = StructureSizeEstimator.fromVolume(
            templates.maximumVolume(), integer(encodedStructure, "max_distance_from_center"), integer(encodedStructure, "size"));
        double placementRarity = settings.rarityInference() && structure != null
            ? estimateRarity(sourceId, registries, resources, evidence, resourceDependencies)
            : 0.50D;
        if (!settings.rarityInference()) evidence.add("rarity inference disabled; neutral score used");
        boolean bossLike = archetypes.contains(RecipeArchetype.BOSS) || archetypes.contains(RecipeArchetype.ARENA);
        double rarity = PlacementRarityEstimator.combine(placementRarity, size, bossLike);

        Optional<ResourceKey<Level>> dimension = Optional.empty();
        if (settings.dimensionInference()) {
            if (themes.contains(RecipeTheme.NETHER)) dimension = Optional.of(Level.NETHER);
            else if (themes.contains(RecipeTheme.END)) dimension = Optional.of(Level.END);
            else if (structure != null) dimension = Optional.of(Level.OVERWORLD);
        }
        if (themes.isEmpty()) themes.add(RecipeTheme.OVERWORLD);
        boolean fallback = structure == null && templates.templatesAnalyzed() == 0;
        if (fallback) evidence.add("structure registry and matching templates exposed no analyzable data");
        return new StructureRecipeAnalysis(
            new StructureRecipeProfile(
                dungeonId, rarity, size, themes, archetypes, candidates, dimension, deduplicate(evidence),
                templates.templatesAnalyzed() > 0, fallback),
            resourceDependencies);
    }

    private static void analyzeBiomes(
        Structure structure,
        Set<RecipeTheme> themes,
        Set<RecipeArchetype> archetypes,
        List<String> evidence
    ) {
        int count = 0;
        double temperature = 0.0D;
        for (Holder<Biome> biome : structure.biomes()) {
            count++;
            temperature += biome.value().getBaseTemperature();
            biome.unwrapKey().ifPresent(key -> merge(
                SemanticThemeInference.fromSemanticText(key.location().toString(), "biome ID"), themes, archetypes, evidence));
            biome.tags().forEach(tag -> merge(
                SemanticThemeInference.fromSemanticText(tag.location().toString(), "biome tag"), themes, archetypes, evidence));
        }
        if (count > 0) {
            double average = temperature / count;
            if (average <= 0.20D) themes.add(RecipeTheme.COLD);
            if (average >= 1.25D) themes.add(RecipeTheme.HOT);
            evidence.add("structure biome restrictions (" + count + " biome(s), average temperature "
                + String.format(java.util.Locale.ROOT, "%.2f", average) + ")");
        }
    }

    private static void analyzeWorldgenEnvironment(Structure structure, Set<RecipeTheme> themes, List<String> evidence) {
        String adaptation = structure.terrainAdaptation().getSerializedName();
        String step = structure.step().getName();
        if (!adaptation.equals("none") || step.contains("underground")) {
            themes.add(RecipeTheme.UNDERGROUND);
            if (step.contains("underground")) themes.add(RecipeTheme.CAVE);
            evidence.add("worldgen step=" + step + ", terrain adaptation=" + adaptation);
        } else if (!themes.contains(RecipeTheme.OCEAN) && !themes.contains(RecipeTheme.NETHER) && !themes.contains(RecipeTheme.END)) {
            themes.add(RecipeTheme.SURFACE);
        }
    }

    private static void applyConfiguredEnvironment(EnvironmentType environment, Set<RecipeTheme> themes, List<String> evidence) {
        if (environment == null) return;
        switch (environment) {
            case UNDERGROUND -> themes.add(RecipeTheme.UNDERGROUND);
            case CAVE -> {
                themes.add(RecipeTheme.CAVE);
                themes.add(RecipeTheme.UNDERGROUND);
            }
            case OCEAN_SURFACE, UNDERWATER -> {
                themes.add(RecipeTheme.OCEAN);
                themes.add(RecipeTheme.WATER);
            }
            case NETHER_LIKE -> themes.add(RecipeTheme.NETHER);
            case END_LIKE, FLOATING_ISLAND -> themes.add(RecipeTheme.END);
            case SURFACE -> themes.add(RecipeTheme.SURFACE);
            case CUSTOM -> { }
        }
        evidence.add("resolved dungeon environment " + environment.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static double estimateRarity(
        ResourceLocation structureId,
        RegistryAccess registries,
        ResourceManager resources,
        List<String> evidence,
        Set<ResourceLocation> resourceDependencies
    ) {
        Registry<StructureSet> sets = registries.registryOrThrow(Registries.STRUCTURE_SET);
        double best = Double.POSITIVE_INFINITY;
        int matches = 0;
        for (Map.Entry<ResourceKey<StructureSet>, StructureSet> setEntry : sets.entrySet()) {
            StructureSet set = setEntry.getValue();
            List<StructureSet.StructureSelectionEntry> matching = set.structures().stream()
                .filter(entry -> entry.structure().is(structureId)).toList();
            if (matching.isEmpty()) continue;
            matches++;
            resourceDependencies.add(structureSetResource(setEntry.getKey().location()));
            int totalWeight = set.structures().stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
            int selectedWeight = matching.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
            PlacementSettings placementSettings = loadPlacementSettings(setEntry.getKey().location(), resources);
            double frequency = placementSettings.frequency();
            boolean exclusion = placementSettings.exclusion();
            double score = placementRarity(set.placement(), frequency, selectedWeight, totalWeight, exclusion);
            if (set.placement() instanceof RandomSpreadStructurePlacement random) {
                evidence.add("random-spread placement " + setEntry.getKey().location() + " spacing=" + random.spacing()
                    + ", separation=" + random.separation() + ", frequency=" + frequency
                    + ", weight=" + selectedWeight + "/" + totalWeight + (exclusion ? ", exclusion zone" : ""));
            } else if (set.placement() instanceof ConcentricRingsStructurePlacement rings) {
                evidence.add("concentric-rings placement " + setEntry.getKey().location() + " distance=" + rings.distance()
                    + ", spread=" + rings.spread() + ", count=" + rings.count());
            } else {
                evidence.add("custom placement " + setEntry.getKey().location() + " frequency=" + frequency);
            }
            best = Math.min(best, score);
        }
        if (matches == 0) {
            evidence.add("no structure-set placement found; neutral rarity used");
            return 0.50D;
        }
        return best;
    }

    static double placementRarity(
        StructurePlacement placement,
        double frequency,
        int selectedWeight,
        int totalWeight,
        boolean exclusion
    ) {
        if (placement instanceof RandomSpreadStructurePlacement random) {
            return PlacementRarityEstimator.randomSpread(
                random.spacing(), frequency, selectedWeight, totalWeight, exclusion);
        }
        if (placement instanceof ConcentricRingsStructurePlacement rings) {
            return PlacementRarityEstimator.concentricRings(rings.distance(), rings.spread(), rings.count());
        }
        return Math.max(0.0D, Math.min(
            1.0D, 0.50D + (1.0D - frequency) * 0.35D + (exclusion ? 0.08D : 0.0D)));
    }

    private static PlacementSettings loadPlacementSettings(ResourceLocation structureSetId, ResourceManager resources) {
        ResourceLocation resourceId = ResourceLocation.fromNamespaceAndPath(
            structureSetId.getNamespace(), "worldgen/structure_set/" + structureSetId.getPath() + ".json");
        Optional<Resource> resource = resources.getResource(resourceId);
        if (resource.isEmpty()) return PlacementSettings.DEFAULT;
        try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) return PlacementSettings.DEFAULT;
            JsonElement placementElement = root.getAsJsonObject().get("placement");
            if (placementElement == null || !placementElement.isJsonObject()) return PlacementSettings.DEFAULT;
            JsonObject placement = placementElement.getAsJsonObject();
            double frequency = placement.has("frequency") ? placement.get("frequency").getAsDouble() : 1.0D;
            return new PlacementSettings(frequency, placement.has("exclusion_zone"));
        } catch (IOException | RuntimeException exception) {
            InstancedNotInfinite.LOGGER.debug(
                "Could not inspect structure-set placement {} for recipe rarity: {}", structureSetId, exception.getMessage());
            return PlacementSettings.DEFAULT;
        }
    }

    private static TemplateAnalysis analyzeTemplates(
        ResourceLocation sourceId,
        boolean registeredStructure,
        JsonElement encodedStructure,
        RegistryAccess registries,
        ResourceManager resources,
        StructureTemplateManager manager,
        Set<ResourceLocation> resourceDependencies
    ) {
        Map<ResourceLocation, Long> blocks = new HashMap<>();
        Set<ResourceLocation> analyzed = new HashSet<>();
        Queue<ResourceLocation> pools = new ArrayDeque<>();
        Set<ResourceLocation> visitedPools = new HashSet<>();
        long maximumVolume = 0L;
        int loadedTemplates = 0;

        collectFieldIds(encodedStructure, Set.of("start_pool"), pools);
        LinkedHashSet<ResourceLocation> candidates = initialTemplateCandidates(sourceId, registeredStructure, resources, manager);
        Registry<StructureTemplatePool> poolRegistry = registries.registryOrThrow(Registries.TEMPLATE_POOL);
        while ((!pools.isEmpty() || !candidates.isEmpty()) && analyzed.size() < MAX_TEMPLATES) {
            while (!pools.isEmpty() && visitedPools.size() < MAX_POOLS) {
                ResourceLocation poolId = pools.remove();
                if (!visitedPools.add(poolId)) continue;
                resourceDependencies.add(templatePoolResource(poolId));
                StructureTemplatePool pool = poolRegistry.get(poolId);
                if (pool == null) continue;
                JsonElement encoded = StructureTemplatePool.DIRECT_CODEC
                    .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), pool).result().orElse(null);
                collectFieldIds(encoded, Set.of("fallback"), pools);
                collectElementTemplateIds(encoded, candidates);
            }
            if (candidates.isEmpty()) break;
            ResourceLocation templateId = candidates.removeFirst();
            if (!analyzed.add(templateId)) continue;
            resourceDependencies.add(structureTemplateResource(templateId));
            Optional<CompoundTag> template = loadTemplate(templateId, resources, manager);
            if (template.isEmpty()) continue;
            loadedTemplates++;
            TemplateNbtAnalysis result = analyzeTemplateNbt(template.get());
            result.blockFrequencies().forEach((id, count) -> blocks.merge(id, count, Long::sum));
            result.referencedPools().forEach(pool -> {
                if (!visitedPools.contains(pool)) pools.add(pool);
            });
            maximumVolume = Math.max(maximumVolume, result.volume());
        }
        return new TemplateAnalysis(Map.copyOf(blocks), loadedTemplates, maximumVolume);
    }

    private static LinkedHashSet<ResourceLocation> initialTemplateCandidates(
        ResourceLocation sourceId,
        boolean registeredStructure,
        ResourceManager resources,
        StructureTemplateManager manager
    ) {
        Set<String> sourceTokens = SemanticThemeInference.tokens(sourceId.getPath());
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        result.add(sourceId);
        List<ResourceLocation> available;
        if (manager != null) {
            available = manager.listTemplates().sorted().toList();
        } else {
            available = resources.listResources("structure", id -> id.getPath().endsWith(".nbt")).keySet().stream()
                .map(StructureRecipeAnalyzer::templateIdFromResource).filter(Optional::isPresent).map(Optional::get).sorted().toList();
        }
        if (registeredStructure) {
            available.stream()
                .filter(id -> id.getNamespace().equals(sourceId.getNamespace()))
                .filter(id -> SemanticThemeInference.tokens(id.getPath()).stream().anyMatch(sourceTokens::contains))
                .limit(256)
                .forEach(result::add);
        }
        return result;
    }

    private static Optional<CompoundTag> loadTemplate(
        ResourceLocation templateId,
        ResourceManager resources,
        StructureTemplateManager manager
    ) {
        if (manager != null) {
            return manager.get(templateId).map(template -> template.save(new CompoundTag()));
        }
        ResourceLocation resourceId = ResourceLocation.fromNamespaceAndPath(
            templateId.getNamespace(), "structure/" + templateId.getPath() + ".nbt");
        Optional<Resource> resource = resources.getResource(resourceId);
        if (resource.isEmpty()) return Optional.empty();
        try (InputStream stream = resource.get().open()) {
            return Optional.of(NbtIo.readCompressed(stream, NbtAccounter.create(64L * 1024L * 1024L)));
        } catch (IOException | RuntimeException exception) {
            InstancedNotInfinite.LOGGER.debug("Could not inspect structure template {}: {}", templateId, exception.getMessage());
            return Optional.empty();
        }
    }

    private static TemplateNbtAnalysis analyzeTemplateNbt(CompoundTag root) {
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        if (palette.isEmpty()) {
            ListTag palettes = root.getList("palettes", Tag.TAG_LIST);
            if (!palettes.isEmpty()) palette = palettes.getList(0);
        }
        List<ResourceLocation> states = new ArrayList<>(palette.size());
        for (int index = 0; index < palette.size(); index++) {
            states.add(ResourceLocation.tryParse(palette.getCompound(index).getString("Name")));
        }
        Map<ResourceLocation, Long> frequencies = new HashMap<>();
        Set<ResourceLocation> pools = new HashSet<>();
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        int limit = Math.min(blocks.size(), MAX_TEMPLATE_BLOCKS);
        for (int index = 0; index < limit; index++) {
            CompoundTag block = blocks.getCompound(index);
            int state = block.getInt("state");
            if (state < 0 || state >= states.size()) continue;
            ResourceLocation blockId = states.get(state);
            if (blockId == null) continue;
            frequencies.merge(blockId, 1L, Long::sum);
            if (blockId.equals(ResourceLocation.withDefaultNamespace("jigsaw")) && block.contains("nbt", Tag.TAG_COMPOUND)) {
                CompoundTag nbt = block.getCompound("nbt");
                ResourceLocation pool = ResourceLocation.tryParse(nbt.getString("pool"));
                if (pool != null && !pool.getPath().equals("empty")) pools.add(pool);
            }
        }
        ListTag size = root.getList("size", Tag.TAG_INT);
        long volume = size.size() >= 3 ? (long)size.getInt(0) * size.getInt(1) * size.getInt(2) : 0L;
        return new TemplateNbtAnalysis(Map.copyOf(frequencies), Set.copyOf(pools), Math.max(0L, volume));
    }

    private static Map<ResourceLocation, ResourceLocation> obtainableItemForms(Set<ResourceLocation> blockIds) {
        Map<ResourceLocation, ResourceLocation> result = new HashMap<>();
        for (ResourceLocation blockId : blockIds) {
            Block block = BuiltInRegistries.BLOCK.get(blockId);
            if (block == null || block.asItem() == Items.AIR) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(block.asItem());
            if (itemId != null && !itemId.equals(ResourceLocation.withDefaultNamespace("air"))) result.put(blockId, itemId);
        }
        return result;
    }

    private static void collectElementTemplateIds(JsonElement element, Set<ResourceLocation> output) {
        if (element == null || !element.isJsonObject() && !element.isJsonArray()) return;
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectElementTemplateIds(child, output));
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("location") && object.get("location").isJsonPrimitive()) {
            ResourceLocation id = ResourceLocation.tryParse(object.get("location").getAsString());
            if (id != null) output.add(id);
        }
        object.entrySet().forEach(entry -> collectElementTemplateIds(entry.getValue(), output));
    }

    private static void collectFieldIds(JsonElement element, Set<String> fields, java.util.Collection<ResourceLocation> output) {
        if (element == null) return;
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectFieldIds(child, fields, output));
        } else if (element.isJsonObject()) {
            element.getAsJsonObject().entrySet().forEach(entry -> {
                if (fields.contains(entry.getKey()) && entry.getValue().isJsonPrimitive()) {
                    ResourceLocation id = ResourceLocation.tryParse(entry.getValue().getAsString());
                    if (id != null) output.add(id);
                }
                collectFieldIds(entry.getValue(), fields, output);
            });
        }
    }

    private static int integer(JsonElement element, String field) {
        if (element == null) return 0;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has(field) && object.get(field).isJsonPrimitive()) {
                try {
                    return object.get(field).getAsInt();
                } catch (RuntimeException ignored) { }
            }
            return object.entrySet().stream().mapToInt(entry -> integer(entry.getValue(), field)).max().orElse(0);
        }
        if (element.isJsonArray()) return element.getAsJsonArray().asList().stream().mapToInt(value -> integer(value, field)).max().orElse(0);
        return 0;
    }

    private static Optional<ResourceLocation> templateIdFromResource(ResourceLocation resource) {
        String path = resource.getPath();
        if (!path.startsWith("structure/") || !path.endsWith(".nbt")) return Optional.empty();
        return Optional.of(ResourceLocation.fromNamespaceAndPath(
            resource.getNamespace(), path.substring("structure/".length(), path.length() - ".nbt".length())));
    }

    static ResourceLocation structureResource(ResourceLocation structureId) {
        return ResourceLocation.fromNamespaceAndPath(
            structureId.getNamespace(), "worldgen/structure/" + structureId.getPath() + ".json");
    }

    static ResourceLocation structureSetResource(ResourceLocation structureSetId) {
        return ResourceLocation.fromNamespaceAndPath(
            structureSetId.getNamespace(), "worldgen/structure_set/" + structureSetId.getPath() + ".json");
    }

    static ResourceLocation templatePoolResource(ResourceLocation poolId) {
        return ResourceLocation.fromNamespaceAndPath(
            poolId.getNamespace(), "worldgen/template_pool/" + poolId.getPath() + ".json");
    }

    static ResourceLocation structureTemplateResource(ResourceLocation templateId) {
        return ResourceLocation.fromNamespaceAndPath(
            templateId.getNamespace(), "structure/" + templateId.getPath() + ".nbt");
    }

    private static void merge(
        ThemeInferenceResult result,
        Set<RecipeTheme> themes,
        Set<RecipeArchetype> archetypes,
        List<String> evidence
    ) {
        themes.addAll(result.themes());
        archetypes.addAll(result.archetypes());
        evidence.addAll(result.evidence());
    }

    private static List<String> deduplicate(List<String> values) {
        return values.stream().distinct().toList();
    }

    private record TemplateAnalysis(Map<ResourceLocation, Long> blockFrequencies, int templatesAnalyzed, long maximumVolume) {
        static TemplateAnalysis empty() {
            return new TemplateAnalysis(Map.of(), 0, 0L);
        }
    }

    private record TemplateNbtAnalysis(Map<ResourceLocation, Long> blockFrequencies, Set<ResourceLocation> referencedPools, long volume) {
    }

    private record PlacementSettings(double frequency, boolean exclusion) {
        private static final PlacementSettings DEFAULT = new PlacementSettings(1.0D, false);
    }
}
