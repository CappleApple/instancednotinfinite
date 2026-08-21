package com.cappleapple.instancednotinfinite.definition;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class DungeonDefinitionRegistry extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final DungeonDefinitionRegistry INSTANCE = new DungeonDefinitionRegistry();

    private volatile Map<ResourceLocation, DungeonDefinition> legacyDefinitions = Map.of();
    private volatile Map<ResourceLocation, DungeonDefinition> automaticDefinitions = Map.of();
    private volatile Map<ResourceLocation, DungeonDefinition> definitions = Map.of();
    private volatile Map<ResourceLocation, AutomaticDungeonMetadata> automaticMetadata = Map.of();
    private volatile Map<String, DungeonOverride> configuredOverrides = Map.of();
    private volatile Map<ResourceLocation, List<ResourceLocation>> structurePools = Map.of();
    private volatile Set<ResourceLocation> poolOnlySuppressedDungeons = Set.of();

    private DungeonDefinitionRegistry() {
        super(GSON, "instanced_dungeons");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, DungeonDefinition> loaded = new LinkedHashMap<>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                DungeonDefinition definition = DefinitionParser.parse(
                    entry.getKey().toString(), entry.getValue(),
                    configuredOrDefault(ServerConfig.INSTANCE.defaultHorizontalPadding),
                    configuredOrDefault(ServerConfig.INSTANCE.defaultVerticalPadding));
                loaded.put(entry.getKey(), definition);
            } catch (DefinitionException exception) {
                InstancedNotInfinite.LOGGER.error(
                    "Invalid dungeon definition {} field {}: {}",
                    entry.getKey(), exception.field(), exception.getMessage());
            } catch (RuntimeException exception) {
                InstancedNotInfinite.LOGGER.error("Unexpected error loading dungeon definition {}", entry.getKey(), exception);
            }
        });
        this.legacyDefinitions = Map.copyOf(loaded);
        combine();
        InstancedNotInfinite.LOGGER.info("Loaded {} valid dungeon definitions ({} rejected)", loaded.size(), resources.size() - loaded.size());
    }

    public synchronized void rebuildAutomatic(MinecraftServer server) {
        rebuildAutomatic(server.registryAccess());
    }

    public synchronized void rebuildAutomatic(RegistryAccess registryAccess) {
        Registry<Structure> structures = registryAccess.registryOrThrow(Registries.STRUCTURE);
        List<String> configuredTags = configuredList(ServerConfig.INSTANCE.structureTags);
        List<String> poolOnlyTags = configuredList(ServerConfig.INSTANCE.poolItemOnlyStructureTags);
        LinkedHashSet<String> allTags = new LinkedHashSet<>(configuredTags);
        allTags.addAll(poolOnlyTags);
        ConfiguredStructureSelector.Result selection = ConfiguredStructureSelector.resolve(
            configuredList(ServerConfig.INSTANCE.structures),
            List.copyOf(allTags),
            configuredList(ServerConfig.INSTANCE.excludedStructures),
            id -> {
                ResourceLocation location = ResourceLocation.tryParse(id);
                return location != null && structures.containsKey(location);
            },
            tagId -> resolveTag(structures, tagId));
        selection.diagnostics().forEach(message -> InstancedNotInfinite.LOGGER.warn("Dungeon catalogue: {}", message));

        DungeonOverrideParser.Result parsedOverrides = DungeonOverrideParser.parse(configuredList(ServerConfig.INSTANCE.dungeonOverrides));
        parsedOverrides.diagnostics().forEach(message -> InstancedNotInfinite.LOGGER.warn("Dungeon catalogue: {}", message));
        this.configuredOverrides = parsedOverrides.overrides();

        Map<ResourceLocation, DungeonDefinition> loaded = new LinkedHashMap<>();
        Map<ResourceLocation, AutomaticDungeonMetadata> metadata = new LinkedHashMap<>();
        int rejected = 0;
        for (String rawId : selection.structureIds()) {
            ResourceLocation structureId = ResourceLocation.parse(rawId);
            try {
                ResolvedDungeonOption option = AutomaticDungeonResolver.resolve(
                    registryAccess, structureId, selection.sources().getOrDefault(rawId, List.of()),
                    parsedOverrides.overrides().get(rawId),
                    configuredOrDefault(ServerConfig.INSTANCE.defaultHorizontalPadding),
                    configuredOrDefault(ServerConfig.INSTANCE.defaultVerticalPadding),
                    configuredOrDefault(ServerConfig.INSTANCE.maximumTerrainRadius));
                loaded.put(structureId, option.definition());
                metadata.put(structureId, option.metadata());
                if (configuredOrDefault(ServerConfig.INSTANCE.debugLogging)) {
                    InstancedNotInfinite.LOGGER.info(
                        "Automatic dungeon {}: biomes={} environment={} reason='{}' padding={}/{} type={} adaptation={} step={}",
                        structureId, option.metadata().resolvedBiomeCount(), option.metadata().environment(),
                        option.metadata().environmentReason(), option.metadata().horizontalPadding(),
                        option.metadata().verticalPadding(), option.metadata().structureType(),
                        option.metadata().terrainAdaptation(), option.metadata().generationStep());
                }
            } catch (RuntimeException | ResolutionException exception) {
                rejected++;
                InstancedNotInfinite.LOGGER.warn("Skipping unsupported automatic dungeon {}: {}", structureId, exception.getMessage());
            }
        }
        this.automaticDefinitions = Map.copyOf(loaded);
        this.automaticMetadata = Map.copyOf(metadata);
        combine();
        rebuildStructurePools(structures, allTags, poolOnlyTags, configuredList(ServerConfig.INSTANCE.structures));
        InstancedNotInfinite.LOGGER.info(
            "Automatic dungeon catalogue contains {} options ({} rejected), {} structure pools, and {} pool-only members; {} advanced datapack definitions are also loaded",
            loaded.size(), rejected, this.structurePools.size(), this.poolOnlySuppressedDungeons.size(), this.legacyDefinitions.size());
    }

    private void rebuildStructurePools(
        Registry<Structure> structures,
        Collection<String> configuredTags,
        Collection<String> poolOnlyTags,
        Collection<String> directStructures
    ) {
        Set<ResourceLocation> direct = directStructures.stream()
            .map(String::trim).map(ResourceLocation::tryParse).filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        Map<ResourceLocation, List<ResourceLocation>> pools = new LinkedHashMap<>();
        for (String raw : configuredTags) {
            String normalized = normalizeTag(raw);
            ResourceLocation tagId = ResourceLocation.tryParse(normalized);
            if (tagId == null || pools.containsKey(tagId)) continue;
            List<ResourceLocation> members = resolveTag(structures, normalized).stream().flatMap(Collection::stream)
                .map(ResourceLocation::tryParse).filter(java.util.Objects::nonNull)
                .filter(this.definitions::containsKey).distinct().sorted().toList();
            if (!members.isEmpty()) pools.put(tagId, members);
        }
        Set<ResourceLocation> suppressed = new LinkedHashSet<>();
        for (String raw : poolOnlyTags) {
            ResourceLocation tagId = ResourceLocation.tryParse(normalizeTag(raw));
            if (tagId != null) suppressed.addAll(pools.getOrDefault(tagId, List.of()));
        }
        suppressed.removeAll(direct);
        this.structurePools = Map.copyOf(pools);
        this.poolOnlySuppressedDungeons = Set.copyOf(suppressed);
    }

    private synchronized void combine() {
        Map<ResourceLocation, DungeonDefinition> combined = new LinkedHashMap<>(this.automaticDefinitions);
        combined.putAll(this.legacyDefinitions);
        this.definitions = Map.copyOf(combined);
    }

    public Optional<DungeonDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(this.definitions.get(id));
    }

    public List<ResourceLocation> ids() {
        return this.definitions.keySet().stream().sorted().toList();
    }

    public Optional<ResourceLocation> select(long seed) {
        return WeightedDungeonSelector.select(this.definitions, seed);
    }

    public Optional<ResourceLocation> selectStructurePool(ResourceLocation tagId, long seed) {
        Map<ResourceLocation, DungeonDefinition> candidates = new LinkedHashMap<>();
        for (ResourceLocation member : this.structurePools.getOrDefault(tagId, List.of())) {
            DungeonDefinition definition = this.definitions.get(member);
            if (definition != null) candidates.put(member, definition);
        }
        return WeightedDungeonSelector.select(candidates, seed);
    }

    public Map<ResourceLocation, List<ResourceLocation>> structurePools() {
        return this.structurePools;
    }

    public List<ResourceLocation> exposedDungeonCatalystIds() {
        return this.definitions.keySet().stream()
            .filter(id -> !this.poolOnlySuppressedDungeons.contains(id)).sorted().toList();
    }

    public boolean poolOnlySuppresses(ResourceLocation dungeonId) {
        return this.poolOnlySuppressedDungeons.contains(dungeonId);
    }

    public int size() {
        return this.definitions.size();
    }

    public boolean isAutomatic(ResourceLocation id) {
        return this.automaticDefinitions.containsKey(id) && !this.legacyDefinitions.containsKey(id);
    }

    public Optional<AutomaticDungeonMetadata> inspect(ResourceLocation id) {
        if (this.legacyDefinitions.containsKey(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.automaticMetadata.get(id));
    }

    public Optional<DungeonOverride> configuredOverride(ResourceLocation id) {
        return Optional.ofNullable(this.configuredOverrides.get(id.toString()));
    }

    public int automaticSize() {
        return (int)this.automaticDefinitions.keySet().stream()
            .filter(id -> !this.legacyDefinitions.containsKey(id))
            .count();
    }

    public int legacySize() {
        return this.legacyDefinitions.size();
    }

    private static int configuredOrDefault(ModConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            // GameTestServer performs its first resource reload before server config loading.
            return value.getDefault();
        }
    }

    private static boolean configuredOrDefault(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return value.getDefault();
        }
    }

    private static List<String> configuredList(ModConfigSpec.ConfigValue<List<? extends String>> value) {
        try {
            return List.copyOf(value.get());
        } catch (IllegalStateException notLoadedYet) {
            return List.copyOf(value.getDefault());
        }
    }

    private static Optional<? extends Collection<String>> resolveTag(Registry<Structure> registry, String tagId) {
        ResourceLocation location = ResourceLocation.tryParse(tagId);
        if (location == null) {
            return Optional.empty();
        }
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, location);
        return registry.getTag(tag).map(set -> set.stream()
            .map(Holder::unwrapKey)
            .flatMap(Optional::stream)
            .map(ResourceKey::location)
            .map(ResourceLocation::toString)
            .sorted()
            .toList());
    }

    private static String normalizeTag(String raw) {
        String value = raw == null ? "" : raw.trim();
        return value.startsWith("#") ? value.substring(1) : value;
    }
}
