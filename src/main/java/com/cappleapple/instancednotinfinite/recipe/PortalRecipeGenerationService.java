package com.cappleapple.instancednotinfinite.recipe;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.content.ManifestationTargetComponent;
import com.cappleapple.instancednotinfinite.content.ModContent;
import com.cappleapple.instancednotinfinite.definition.DungeonDefinition;
import com.cappleapple.instancednotinfinite.definition.DungeonDefinitionRegistry;
import com.cappleapple.instancednotinfinite.definition.DungeonOverride;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class PortalRecipeGenerationService {
    public static final PortalRecipeGenerationService INSTANCE = new PortalRecipeGenerationService();
    private static final ResourceLocation COMMON_TIER = id("common");
    private static final IngredientReference GENERIC = IngredientReference.parse("#instancednotinfinite:recipe_theme/generic");
    private static final IngredientReference COMMON_CORE = IngredientReference.parse("#instancednotinfinite:portal_core/common");
    private static final IngredientReference CATALYST = IngredientReference.parse("#instancednotinfinite:portal_catalyst");
    private static final IngredientReference EMERGENCY_SIGNATURE = IngredientReference.parse("minecraft:obsidian");
    private static final IngredientReference EMERGENCY_CORE = IngredientReference.parse("minecraft:iron_ingot");
    private static final IngredientReference EMERGENCY_CATALYST = IngredientReference.parse("minecraft:ender_pearl");

    private final StructureRecipeAnalyzer analyzer = new StructureRecipeAnalyzer();
    private volatile List<PortalRecipeTier> tiers = List.of(emergencyTier());
    private volatile Map<ResourceLocation, StructureRecipeProfile> profiles = Map.of();
    private volatile Map<ResourceLocation, PortalRecipeReport> reports = Map.of();
    private Set<ResourceLocation> generatedIds = Set.of();
    private RecipeManager lastManager;

    private PortalRecipeGenerationService() {
    }

    public synchronized void updateTiers(List<PortalRecipeTier> loaded) {
        this.tiers = loaded.isEmpty() ? List.of(emergencyTier()) : List.copyOf(loaded);
    }

    public synchronized void rebuild(
        RegistryAccess registries,
        ResourceManager resources,
        RecipeManager manager,
        StructureTemplateManager templates,
        ItemTagLookup tags,
        long worldSeed
    ) {
        RecipeInferenceSettings settings = RecipeInferenceSettings.configured();
        RecipeIngredientExclusions ingredientExclusions = RecipeIngredientExclusions.configured(registries);
        RecipeTargetExclusions targetExclusions = RecipeTargetExclusions.configured(registries);
        boolean sameManager = manager == this.lastManager;
        Map<ResourceLocation, RecipeHolder<?>> base = new LinkedHashMap<>();
        manager.getRecipes().stream().sorted(java.util.Comparator.comparing(RecipeHolder::id)).forEach(recipe -> {
            if (!sameManager || !this.generatedIds.contains(recipe.id())) base.put(recipe.id(), recipe);
        });
        Map<ManifestationTargetComponent, ResourceLocation> explicit = explicitRecipes(base.values(), registries);
        Map<ResourceLocation, StructureRecipeProfile> nextProfiles = new LinkedHashMap<>();
        Map<ResourceLocation, PortalRecipeReport> nextReports = new LinkedHashMap<>();
        Map<ResourceLocation, RecipeHolder<?>> generated = new LinkedHashMap<>();
        Map<ResourceLocation, GeneratedRecipeIngredients> blueprints = new LinkedHashMap<>();

        for (ResourceLocation dungeonId : DungeonDefinitionRegistry.INSTANCE.ids()) {
            DungeonDefinition definition = DungeonDefinitionRegistry.INSTANCE.get(dungeonId).orElseThrow();
            DungeonOverride override = DungeonDefinitionRegistry.INSTANCE.configuredOverride(dungeonId).orElse(null);
            ResourceLocation sourceId = ResourceLocation.parse(definition.structure());
            StructureRecipeProfile profile = analyzer.analyze(
                dungeonId, sourceId, definition.environment(), registries, resources, templates, settings, ingredientExclusions);
            nextProfiles.put(dungeonId, profile);
            List<String> warnings = new ArrayList<>();
            ResourceLocation forcedTier = override == null || override.costTier() == null
                ? null : ResourceLocation.parse(override.costTier());
            Optional<PortalRecipeTier> resolvedTier = PortalRecipeTierResolver.resolve(profile, this.tiers, forcedTier);
            if (forcedTier != null && resolvedTier.stream().noneMatch(tier -> tier.id().equals(forcedTier))) {
                warnings.add("forced tier " + forcedTier + " was not loaded; normal tier matching was used");
            }
            PortalRecipeTier tier = resolvedTier.orElseGet(() -> {
                warnings.add("no datapack tier matched rarity " + profile.rarityScore() + "; emergency common tier used");
                return emergencyTier();
            });

            ResourceLocation explicitRecipe = explicit.get(ManifestationTargetComponent.dungeon(dungeonId));
            boolean explicitOverride = hasExplicitRecipeOverride(override);
            RecipeSource source = PortalRecipePrecedence.choose(
                explicitRecipe != null, explicitOverride, settings.automaticRecipeGeneration(), profile.fallback());
            if (source == RecipeSource.GENERIC_FALLBACK) {
                warnings.add("structure or template data was unavailable; generic inference fallback was used");
            }
            if (source == RecipeSource.DATAPACK) {
                nextReports.put(dungeonId, new PortalRecipeReport(
                    dungeonId, source, profile, Optional.of(explicitRecipe), Optional.of(tier),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), warnings));
                continue;
            }
            if (source == RecipeSource.DISABLED) {
                nextReports.put(dungeonId, new PortalRecipeReport(
                    dungeonId, source, profile, Optional.empty(), Optional.of(tier),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), warnings));
                continue;
            }

            IngredientReference signatureReference = overrideValue(override, DungeonOverride::recipeSignature)
                .map(IngredientReference::parse)
                .orElseGet(() -> profile.signatureMaterials().isEmpty()
                    ? themeReference(selectSignatureTheme(profile.themes()))
                    : new IngredientReference(false, profile.signatureMaterials().getFirst().itemId()));
            IngredientReference themeReference = overrideValue(override, DungeonOverride::recipeTheme)
                .map(IngredientReference::parse)
                .orElseGet(() -> themeReference(selectThemeIngredient(profile.themes())));
            IngredientReference coreReference = overrideValue(override, DungeonOverride::recipeCore)
                .map(IngredientReference::parse).orElse(tier.core());
            IngredientReference catalystReference = overrideValue(override, DungeonOverride::recipeCatalyst)
                .map(IngredientReference::parse).orElse(CATALYST);

            ResolvedIngredient signature = resolveRole(dungeonId, "signature", signatureReference,
                List.of(GENERIC, EMERGENCY_SIGNATURE), tags, ingredientExclusions, warnings);
            ResolvedIngredient theme = resolveRole(dungeonId, "theme", themeReference,
                List.of(GENERIC, EMERGENCY_SIGNATURE), tags, ingredientExclusions, warnings);
            ResolvedIngredient core = resolveRole(dungeonId, "core", coreReference,
                List.of(COMMON_CORE, EMERGENCY_CORE), tags, ingredientExclusions, warnings);
            ResolvedIngredient catalyst = resolveRole(dungeonId, "catalyst", catalystReference,
                List.of(CATALYST, EMERGENCY_CATALYST), tags, ingredientExclusions, warnings);

            GeneratedRecipeIngredients blueprint = new GeneratedRecipeIngredients(signature, theme, core, catalyst);
            boolean automaticSource = source == RecipeSource.AUTO_GENERATED || source == RecipeSource.GENERIC_FALLBACK;
            RecipeSource effectiveSource = source;
            if (DungeonDefinitionRegistry.INSTANCE.poolOnlySuppresses(dungeonId)) {
                effectiveSource = RecipeSource.POOL_ONLY;
                warnings.add("exact catalyst suppressed by poolItemOnlyStructureTags");
            } else if (automaticSource && targetExclusions.excludesDungeon(dungeonId)) {
                effectiveSource = RecipeSource.CONFIG_EXCLUDED;
                warnings.add("automatic recipe suppressed by excludedAutomaticRecipeTargets");
            }
            if (effectiveSource != RecipeSource.CONFIG_EXCLUDED) blueprints.put(dungeonId, blueprint);
            if (effectiveSource == RecipeSource.POOL_ONLY || effectiveSource == RecipeSource.CONFIG_EXCLUDED) {
                nextReports.put(dungeonId, new PortalRecipeReport(
                    dungeonId, effectiveSource, profile, Optional.empty(), Optional.of(tier),
                    Optional.of(signature), Optional.of(theme), Optional.of(core), Optional.of(catalyst), warnings));
                continue;
            }

            ResourceLocation recipeId = generatedId(dungeonId);
            if (base.containsKey(recipeId)) {
                recipeId = ResourceLocation.fromNamespaceAndPath(
                    InstancedNotInfinite.MOD_ID, recipeId.getPath() + "_automatic");
            }
            RecipeHolder<ShapedRecipe> holder = new RecipeHolder<>(recipeId,
                shapedRecipe(ManifestationTargetComponent.dungeon(dungeonId),
                    signature.ingredient(), theme.ingredient(), core.ingredient(), catalyst.ingredient()));
            generated.put(recipeId, holder);
            PortalRecipeReport report = new PortalRecipeReport(
                dungeonId, source, profile, Optional.of(recipeId), Optional.of(tier),
                Optional.of(signature), Optional.of(theme), Optional.of(core), Optional.of(catalyst), warnings);
            nextReports.put(dungeonId, report);
            if (source == RecipeSource.GENERIC_FALLBACK || !warnings.isEmpty()) {
                InstancedNotInfinite.LOGGER.warn("Portal recipe inference for {} used fallback data: {}", dungeonId, warnings);
            }
        }

        int generatedPools = 0;
        for (Map.Entry<ResourceLocation, List<ResourceLocation>> pool : DungeonDefinitionRegistry.INSTANCE.structurePools()
            .entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            ResourceLocation poolId = pool.getKey();
            ManifestationTargetComponent target = ManifestationTargetComponent.structurePool(poolId);
            if (explicit.containsKey(target) || !settings.automaticRecipeGeneration() || targetExclusions.excludesPool(poolId)) {
                continue;
            }
            List<Map.Entry<ResourceLocation, GeneratedRecipeIngredients>> memberBlueprints = pool.getValue().stream()
                .filter(blueprints::containsKey)
                .map(id -> Map.entry(id, blueprints.get(id)))
                .sorted(Map.Entry.comparingByKey()).toList();
            if (memberBlueprints.isEmpty()) {
                List<String> poolWarnings = new ArrayList<>();
                GeneratedRecipeIngredients fallback = new GeneratedRecipeIngredients(
                    resolveRole(poolId, "pool_signature", GENERIC, List.of(EMERGENCY_SIGNATURE), tags, ingredientExclusions, poolWarnings),
                    resolveRole(poolId, "pool_theme", GENERIC, List.of(EMERGENCY_SIGNATURE), tags, ingredientExclusions, poolWarnings),
                    resolveRole(poolId, "pool_core", COMMON_CORE, List.of(EMERGENCY_CORE), tags, ingredientExclusions, poolWarnings),
                    resolveRole(poolId, "pool_catalyst", CATALYST, List.of(EMERGENCY_CATALYST), tags, ingredientExclusions, poolWarnings));
                memberBlueprints = List.of(Map.entry(poolId, fallback));
                InstancedNotInfinite.LOGGER.warn(
                    "Structure pool #{} had no member-generated recipe blueprints; generic ingredients were used", poolId);
            }
            ResourceLocation recipeId = generatedPoolId(poolId);
            if (base.containsKey(recipeId)) {
                recipeId = ResourceLocation.fromNamespaceAndPath(
                    InstancedNotInfinite.MOD_ID, recipeId.getPath() + "_automatic");
            }
            generated.put(recipeId, new RecipeHolder<>(recipeId,
                poolRecipe(target, poolId, memberBlueprints, worldSeed)));
            generatedPools++;
        }

        Map<ResourceLocation, RecipeHolder<?>> combined = new LinkedHashMap<>(base);
        combined.putAll(generated);
        manager.replaceRecipes(combined.values());
        this.generatedIds = Set.copyOf(generated.keySet());
        this.lastManager = manager;
        this.profiles = Map.copyOf(nextProfiles);
        this.reports = Map.copyOf(nextReports);
        InstancedNotInfinite.LOGGER.info(
            "Generated {} exact-dungeon and {} structure-pool portal recipes; {} targeted catalysts use explicit datapack recipes",
            generated.size() - generatedPools, generatedPools, explicit.size());
    }

    public Optional<StructureRecipeProfile> profile(ResourceLocation dungeonId) {
        return Optional.ofNullable(this.profiles.get(dungeonId));
    }

    public Optional<PortalRecipeReport> report(ResourceLocation dungeonId) {
        return Optional.ofNullable(this.reports.get(dungeonId));
    }

    public int generatedCount() {
        return this.generatedIds.size();
    }

    private static Map<ManifestationTargetComponent, ResourceLocation> explicitRecipes(
        Collection<RecipeHolder<?>> recipes,
        RegistryAccess registries
    ) {
        Map<ManifestationTargetComponent, ResourceLocation> result = new LinkedHashMap<>();
        recipes.stream().sorted(java.util.Comparator.comparing(RecipeHolder::id)).forEach(holder -> {
            try {
                ItemStack output = holder.value().getResultItem(registries);
                ManifestationTargetComponent target = output.get(ModContent.MANIFESTATION_TARGET.get());
                if (output.is(ModContent.MANIFESTATION_CATALYST.get()) && target != null) {
                    result.putIfAbsent(target, holder.id());
                }
            } catch (RuntimeException exception) {
                InstancedNotInfinite.LOGGER.debug("Could not inspect recipe {} output for portal precedence: {}",
                    holder.id(), exception.getMessage());
            }
        });
        return result;
    }

    private static ShapedRecipe shapedRecipe(
        ManifestationTargetComponent target,
        Ingredient signature,
        Ingredient theme,
        Ingredient core,
        Ingredient catalyst
    ) {
        ItemStack result = new ItemStack(ModContent.MANIFESTATION_CATALYST.get());
        result.set(ModContent.MANIFESTATION_TARGET.get(), target);
        ShapedRecipePattern pattern = ShapedRecipePattern.of(
            Map.of('S', signature, 'T', theme, 'C', core, 'P', catalyst), "STS", "TCT", "SPS");
        return new ShapedRecipe("instancednotinfinite_portals", CraftingBookCategory.MISC, pattern, result, true);
    }

    private static ShapedRecipe poolRecipe(
        ManifestationTargetComponent target,
        ResourceLocation poolId,
        List<Map.Entry<ResourceLocation, GeneratedRecipeIngredients>> members,
        long worldSeed
    ) {
        Map<Character, Ingredient> keys = new LinkedHashMap<>();
        for (int slot = 0; slot < 9; slot++) {
            int member = PoolRecipeMixer.memberIndex(worldSeed, poolId.toString(), slot, members.size());
            keys.put((char)('A' + slot), members.get(member).getValue().ingredientForSlot(slot));
        }
        ItemStack result = new ItemStack(ModContent.MANIFESTATION_CATALYST.get());
        result.set(ModContent.MANIFESTATION_TARGET.get(), target);
        ShapedRecipePattern pattern = ShapedRecipePattern.of(keys, "ABC", "DEF", "GHI");
        return new ShapedRecipe("instancednotinfinite_portal_pools", CraftingBookCategory.MISC, pattern, result, true);
    }

    private static ResolvedIngredient resolveRole(
        ResourceLocation dungeonId,
        String role,
        IngredientReference requested,
        List<IngredientReference> fallbacks,
        ItemTagLookup tags,
        RecipeIngredientExclusions exclusions,
        List<String> warnings
    ) {
        Optional<ResolvedIngredient> resolved = PortalIngredientResolver.resolve(
            dungeonId, role, requested, tags, exclusions::excludesItem);
        if (resolved.isPresent()) return resolved.get();
        warnings.add(role + " ingredient " + requested + " is empty or unavailable");
        for (IngredientReference fallback : fallbacks) {
            resolved = PortalIngredientResolver.resolve(dungeonId, role, fallback, tags, exclusions::excludesItem);
            if (resolved.isPresent()) return resolved.get();
        }
        throw new IllegalStateException("No safe portal recipe ingredient exists for role " + role);
    }

    private static RecipeTheme selectSignatureTheme(Set<RecipeTheme> themes) {
        return select(themes, List.of(
            RecipeTheme.NETHER, RecipeTheme.END, RecipeTheme.OCEAN, RecipeTheme.DESERT, RecipeTheme.COLD,
            RecipeTheme.JUNGLE, RecipeTheme.FOREST, RecipeTheme.SWAMP, RecipeTheme.CAVE, RecipeTheme.UNDERGROUND,
            RecipeTheme.MOUNTAIN, RecipeTheme.RUINS, RecipeTheme.ANCIENT, RecipeTheme.INDUSTRIAL,
            RecipeTheme.FIRE, RecipeTheme.OVERWORLD, RecipeTheme.SURFACE));
    }

    private static RecipeTheme selectThemeIngredient(Set<RecipeTheme> themes) {
        return select(themes, List.of(
            RecipeTheme.FIRE, RecipeTheme.UNDEAD, RecipeTheme.MAGIC, RecipeTheme.COMBAT, RecipeTheme.NETHER,
            RecipeTheme.END, RecipeTheme.OCEAN, RecipeTheme.DESERT, RecipeTheme.COLD, RecipeTheme.SNOW,
            RecipeTheme.JUNGLE, RecipeTheme.FOREST, RecipeTheme.SWAMP, RecipeTheme.CAVE, RecipeTheme.UNDERGROUND,
            RecipeTheme.MOUNTAIN, RecipeTheme.HOT, RecipeTheme.INDUSTRIAL, RecipeTheme.ANCIENT,
            RecipeTheme.RUINS, RecipeTheme.FORTRESS, RecipeTheme.TEMPLE, RecipeTheme.WATER,
            RecipeTheme.OVERWORLD, RecipeTheme.SURFACE));
    }

    private static RecipeTheme select(Set<RecipeTheme> themes, List<RecipeTheme> order) {
        return order.stream().filter(themes::contains).findFirst().orElse(RecipeTheme.OVERWORLD);
    }

    private static IngredientReference themeReference(RecipeTheme theme) {
        return IngredientReference.parse("#instancednotinfinite:recipe_theme/" + theme.serializedName());
    }

    private static boolean hasExplicitRecipeOverride(DungeonOverride override) {
        return override != null && (override.recipeSignature() != null || override.recipeTheme() != null
            || override.recipeCore() != null || override.recipeCatalyst() != null);
    }

    private static Optional<String> overrideValue(DungeonOverride override, java.util.function.Function<DungeonOverride, String> getter) {
        return Optional.ofNullable(override).map(getter);
    }

    private static ResourceLocation generatedId(ResourceLocation dungeonId) {
        return ResourceLocation.fromNamespaceAndPath(
            InstancedNotInfinite.MOD_ID, "generated_portal/" + dungeonId.getNamespace() + "/" + dungeonId.getPath());
    }

    public static ResourceLocation generatedPoolId(ResourceLocation poolId) {
        return ResourceLocation.fromNamespaceAndPath(
            InstancedNotInfinite.MOD_ID, "generated_portal_pool/" + poolId.getNamespace() + "/" + poolId.getPath());
    }

    private static PortalRecipeTier emergencyTier() {
        return new PortalRecipeTier(COMMON_TIER, Integer.MIN_VALUE, 0.0D, 1.0D, COMMON_CORE, Set.of(), Set.of());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(InstancedNotInfinite.MOD_ID, path);
    }

    private record GeneratedRecipeIngredients(
        ResolvedIngredient signature,
        ResolvedIngredient theme,
        ResolvedIngredient core,
        ResolvedIngredient catalyst
    ) {
        Ingredient ingredientForSlot(int slot) {
            return switch (slot) {
                case 0, 2, 6, 8 -> signature.ingredient();
                case 1, 3, 5 -> theme.ingredient();
                case 4 -> core.ingredient();
                case 7 -> catalyst.ingredient();
                default -> throw new IllegalArgumentException("Pool recipe slot must be 0..8");
            };
        }
    }
}
