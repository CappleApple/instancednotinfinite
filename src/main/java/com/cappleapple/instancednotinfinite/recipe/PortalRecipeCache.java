package com.cappleapple.instancednotinfinite.recipe;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.definition.DungeonDefinition;
import com.cappleapple.instancednotinfinite.definition.DungeonDefinitionRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.fml.loading.FMLPaths;

/** Persistent, pack-shippable cache for the expensive structure-to-recipe analysis phase. */
public final class PortalRecipeCache {
    static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String MISSING_RESOURCE = "missing";
    private static final String DIRECTORY = "generated-recipes";

    public String inputFingerprint(
        RegistryAccess registries,
        ResourceManager resources,
        StructureTemplateManager templates,
        RecipeInferenceSettings settings,
        RecipeIngredientExclusions exclusions
    ) {
        List<String> inputs = new ArrayList<>();
        inputs.add("format=" + FORMAT_VERSION);
        inputs.add("paletteInference=" + settings.paletteInference());
        inputs.add("biomeInference=" + settings.biomeInference());
        inputs.add("dimensionInference=" + settings.dimensionInference());
        inputs.add("nameInference=" + settings.nameInference());
        inputs.add("rarityInference=" + settings.rarityInference());
        exclusions.blockIds().stream().sorted().forEach(id -> inputs.add("excludedBlock=" + id));

        Registry<Structure> structures = registries.registryOrThrow(Registries.STRUCTURE);
        Set<ResourceLocation> sourceIds = new LinkedHashSet<>();
        for (ResourceLocation dungeonId : DungeonDefinitionRegistry.INSTANCE.ids()) {
            DungeonDefinition definition = DungeonDefinitionRegistry.INSTANCE.get(dungeonId).orElseThrow();
            ResourceLocation sourceId = ResourceLocation.parse(definition.structure());
            sourceIds.add(sourceId);
            inputs.add("dungeon=" + dungeonId + "|structure=" + sourceId + "|environment=" + definition.environment());
            Structure structure = structures.get(sourceId);
            if (structure == null) {
                inputs.add("structure=" + sourceId + "|missing");
                continue;
            }
            JsonElement encoded = Structure.DIRECT_CODEC
                .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), structure)
                .resultOrPartial(message -> InstancedNotInfinite.LOGGER.debug(
                    "Could not encode structure {} for recipe cache fingerprint: {}", sourceId, message))
                .orElse(null);
            inputs.add("structure=" + sourceId + "|codec=" + (encoded == null ? "unavailable" : GSON.toJson(encoded)));
            structure.biomes().stream().map(PortalRecipeCache::biomeFingerprint).sorted()
                .forEach(value -> inputs.add("structure=" + sourceId + "|" + value));
        }

        DungeonDefinitionRegistry.INSTANCE.structurePools().entrySet().stream().sorted(Map.Entry.comparingByKey())
            .forEach(entry -> inputs.add("pool=" + entry.getKey() + "|members="
                + entry.getValue().stream().sorted().map(ResourceLocation::toString).toList()));

        templateDiscoveryInputs(resources, templates, sourceIds).forEach(value -> inputs.add("templateCandidate=" + value));
        structureSetInputs(registries, sourceIds).forEach(value -> inputs.add("structureSet=" + value));
        return RecipeCacheFingerprint.digest(inputs);
    }

    public Optional<Loaded> load(
        String fingerprint,
        Collection<ResourceLocation> expectedDungeons,
        ResourceManager resources
    ) {
        Path path = cachePath(fingerprint);
        if (!Files.isRegularFile(path)) {
            InstancedNotInfinite.LOGGER.info("No generated recipe cache matched {}; structure analysis is required", path);
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            CacheDocument document = GSON.fromJson(reader, CacheDocument.class);
            validateDocument(document, fingerprint, expectedDungeons);
            for (Map.Entry<String, String> dependency : document.dependencies().entrySet()) {
                ResourceLocation resourceId = ResourceLocation.tryParse(dependency.getKey());
                if (resourceId == null || !dependency.getValue().equals(resourceStamp(resources, resourceId))) {
                    InstancedNotInfinite.LOGGER.info(
                        "Generated recipe cache {} is stale because dependency {} changed", path, dependency.getKey());
                    return Optional.empty();
                }
            }
            Map<ResourceLocation, StructureRecipeProfile> profiles = new LinkedHashMap<>();
            for (ProfileEntry entry : document.recipes()) {
                ResourceLocation dungeonId = requiredId(entry.dungeon(), "recipe dungeon");
                profiles.put(dungeonId, entry.toProfile(dungeonId));
            }
            InstancedNotInfinite.LOGGER.info(
                "Loaded {} generated recipe analyses from {}; structure template scanning was skipped",
                profiles.size(), path);
            return Optional.of(new Loaded(Map.copyOf(profiles), path));
        } catch (IOException | RuntimeException exception) {
            InstancedNotInfinite.LOGGER.warn(
                "Could not use generated recipe cache {}; structure analysis will rebuild it: {}", path, exception.getMessage());
            return Optional.empty();
        }
    }

    public boolean save(
        String fingerprint,
        Map<ResourceLocation, StructureRecipeProfile> profiles,
        Set<ResourceLocation> resourceDependencies,
        ResourceManager resources
    ) {
        Path path = cachePath(fingerprint);
        Path temporary = null;
        try {
            Files.createDirectories(path.getParent());
            Map<String, String> dependencies = new LinkedHashMap<>();
            resourceDependencies.stream().sorted().forEach(resourceId -> {
                try {
                    dependencies.put(resourceId.toString(), resourceStamp(resources, resourceId));
                } catch (IOException exception) {
                    throw new CacheWriteException(exception);
                }
            });
            List<ProfileEntry> recipes = profiles.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> ProfileEntry.from(entry.getKey(), entry.getValue())).toList();
            CacheDocument document = new CacheDocument(FORMAT_VERSION, fingerprint, dependencies, recipes);
            temporary = Files.createTempFile(path.getParent(), "recipe-cache-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(document, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            InstancedNotInfinite.LOGGER.info(
                "Saved {} generated recipe analyses and {} dependency fingerprints to {}",
                profiles.size(), dependencies.size(), path);
            return true;
        } catch (IOException | RuntimeException exception) {
            Throwable cause = exception instanceof CacheWriteException wrapped ? wrapped.getCause() : exception;
            InstancedNotInfinite.LOGGER.warn("Could not save generated recipe cache {}: {}", path, cause.getMessage());
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) { }
            }
        }
    }

    public Path cachePath(String fingerprint) {
        return FMLPaths.CONFIGDIR.get().resolve(InstancedNotInfinite.MOD_ID).resolve(DIRECTORY)
            .resolve(fingerprint + ".json");
    }

    private static void validateDocument(
        CacheDocument document,
        String fingerprint,
        Collection<ResourceLocation> expectedDungeons
    ) {
        if (document == null || document.format() != FORMAT_VERSION || !fingerprint.equals(document.fingerprint())
            || document.dependencies() == null || document.recipes() == null) {
            throw new IllegalArgumentException("cache header is invalid or obsolete");
        }
        Set<String> expected = expectedDungeons.stream().map(ResourceLocation::toString)
            .collect(java.util.stream.Collectors.toSet());
        Set<String> actual = document.recipes().stream().map(ProfileEntry::dungeon)
            .collect(java.util.stream.Collectors.toSet());
        if (!actual.equals(expected) || actual.size() != document.recipes().size()) {
            throw new IllegalArgumentException("cached dungeon set does not match the active catalogue");
        }
    }

    private static List<String> templateDiscoveryInputs(
        ResourceManager resources,
        StructureTemplateManager templates,
        Set<ResourceLocation> sourceIds
    ) {
        List<ResourceLocation> available = templates == null
            ? resources.listResources("structure", id -> id.getPath().endsWith(".nbt")).keySet().stream()
                .map(PortalRecipeCache::templateIdFromResource).flatMap(Optional::stream).sorted().toList()
            : templates.listTemplates().sorted().toList();
        List<String> result = new ArrayList<>();
        for (ResourceLocation sourceId : sourceIds.stream().sorted().toList()) {
            Set<String> sourceTokens = SemanticThemeInference.tokens(sourceId.getPath());
            available.stream().filter(id -> id.getNamespace().equals(sourceId.getNamespace()))
                .filter(id -> SemanticThemeInference.tokens(id.getPath()).stream().anyMatch(sourceTokens::contains))
                .limit(256).forEach(id -> result.add(sourceId + "->" + id));
        }
        return result;
    }

    private static List<String> structureSetInputs(RegistryAccess registries, Set<ResourceLocation> sourceIds) {
        Registry<StructureSet> sets = registries.registryOrThrow(Registries.STRUCTURE_SET);
        List<String> result = new ArrayList<>();
        sets.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(key -> key.location().toString())))
            .forEach(entry -> {
                List<String> selections = entry.getValue().structures().stream()
                    .flatMap(selection -> selection.structure().unwrapKey().stream()
                        .map(key -> key.location() + "@" + selection.weight()))
                    .filter(value -> sourceIds.contains(ResourceLocation.parse(value.substring(0, value.lastIndexOf('@')))))
                    .sorted().toList();
                if (selections.isEmpty()) return;
                String placement = entry.getValue().placement().getClass().getName();
                if (entry.getValue().placement() instanceof RandomSpreadStructurePlacement random) {
                    placement += "|spacing=" + random.spacing() + "|separation=" + random.separation();
                } else if (entry.getValue().placement() instanceof ConcentricRingsStructurePlacement rings) {
                    placement += "|distance=" + rings.distance() + "|spread=" + rings.spread() + "|count=" + rings.count();
                }
                result.add(entry.getKey().location() + "|selections=" + selections + "|placement=" + placement);
            });
        return result;
    }

    private static String biomeFingerprint(Holder<Biome> biome) {
        String id = biome.unwrapKey().map(key -> key.location().toString()).orElse("unregistered");
        List<String> tags = biome.tags().map(tag -> tag.location().toString()).sorted().toList();
        return "biome=" + id + "|temperature=" + biome.value().getBaseTemperature() + "|tags=" + tags;
    }

    private static Optional<ResourceLocation> templateIdFromResource(ResourceLocation resource) {
        String path = resource.getPath();
        if (!path.startsWith("structure/") || !path.endsWith(".nbt")) return Optional.empty();
        return Optional.of(ResourceLocation.fromNamespaceAndPath(
            resource.getNamespace(), path.substring("structure/".length(), path.length() - ".nbt".length())));
    }

    private static String resourceStamp(ResourceManager resources, ResourceLocation resourceId) throws IOException {
        Optional<Resource> resource = resources.getResource(resourceId);
        if (resource.isEmpty()) return MISSING_RESOURCE;
        try (InputStream input = resource.get().open()) {
            MessageDigest digest = sha256();
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) digest.update(buffer, 0, count);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static ResourceLocation requiredId(String value, String field) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalArgumentException(field + " is not a resource ID");
        return id;
    }

    public record Loaded(Map<ResourceLocation, StructureRecipeProfile> profiles, Path path) {
        public Loaded {
            profiles = Map.copyOf(profiles);
        }
    }

    private record CacheDocument(
        int format,
        String fingerprint,
        Map<String, String> dependencies,
        List<ProfileEntry> recipes
    ) {
    }

    private record ProfileEntry(
        String dungeon,
        double rarity,
        double size,
        List<String> themes,
        List<String> archetypes,
        List<MaterialEntry> signatureMaterials,
        String dimension,
        List<String> evidence,
        boolean paletteAnalyzed,
        boolean fallback
    ) {
        static ProfileEntry from(ResourceLocation dungeonId, StructureRecipeProfile profile) {
            return new ProfileEntry(
                dungeonId.toString(), profile.rarityScore(), profile.sizeScore(),
                profile.themes().stream().map(RecipeTheme::serializedName).sorted().toList(),
                profile.archetypes().stream().map(RecipeArchetype::serializedName).sorted().toList(),
                profile.signatureMaterials().stream().map(MaterialEntry::from).toList(),
                profile.dimension().map(key -> key.location().toString()).orElse(null),
                profile.evidence(), profile.paletteAnalyzed(), profile.fallback());
        }

        StructureRecipeProfile toProfile(ResourceLocation dungeonId) {
            if (!Double.isFinite(rarity) || !Double.isFinite(size) || themes == null || archetypes == null
                || signatureMaterials == null || evidence == null) {
                throw new IllegalArgumentException("cached profile " + dungeonId + " is incomplete");
            }
            EnumSet<RecipeTheme> parsedThemes = EnumSet.noneOf(RecipeTheme.class);
            themes.forEach(value -> parsedThemes.add(RecipeTheme.parse(value)
                .orElseThrow(() -> new IllegalArgumentException("unknown cached recipe theme " + value))));
            EnumSet<RecipeArchetype> parsedArchetypes = EnumSet.noneOf(RecipeArchetype.class);
            archetypes.forEach(value -> parsedArchetypes.add(RecipeArchetype.parse(value)
                .orElseThrow(() -> new IllegalArgumentException("unknown cached recipe archetype " + value))));
            Optional<ResourceKey<Level>> parsedDimension = Optional.ofNullable(dimension)
                .map(value -> ResourceKey.create(Registries.DIMENSION, requiredId(value, "profile dimension")));
            return new StructureRecipeProfile(
                dungeonId, rarity, size, parsedThemes, parsedArchetypes,
                signatureMaterials.stream().map(MaterialEntry::toCandidate).toList(), parsedDimension,
                evidence, paletteAnalyzed, fallback);
        }
    }

    private record MaterialEntry(String item, long occurrences, double score, String reason) {
        static MaterialEntry from(MaterialCandidate candidate) {
            return new MaterialEntry(
                candidate.itemId().toString(), candidate.occurrences(), candidate.score(), candidate.reason());
        }

        MaterialCandidate toCandidate() {
            if (!Double.isFinite(score) || reason == null) {
                throw new IllegalArgumentException("cached material candidate is invalid");
            }
            return new MaterialCandidate(requiredId(item, "material item"), occurrences, score, reason);
        }
    }

    private static final class CacheWriteException extends RuntimeException {
        CacheWriteException(IOException cause) {
            super(cause);
        }

        @Override
        public synchronized IOException getCause() {
            return (IOException)super.getCause();
        }
    }
}
