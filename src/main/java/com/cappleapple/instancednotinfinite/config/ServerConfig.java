package com.cappleapple.instancednotinfinite.config;

import com.cappleapple.instancednotinfinite.manifestation.AnimationMode;
import com.cappleapple.instancednotinfinite.manifestation.CatalystConsumptionPolicy;
import com.cappleapple.instancednotinfinite.manifestation.PortalColor;
import com.cappleapple.instancednotinfinite.manifestation.ParticleColor;
import com.cappleapple.instancednotinfinite.manifestation.PreparationParticleStyle;
import com.cappleapple.instancednotinfinite.instance.InstanceLifecycleSettings;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ServerConfig INSTANCE;

    public final ModConfigSpec.IntValue vacancyTimeoutSeconds;
    public final ModConfigSpec.IntValue postVisitVacancyTimeoutSeconds;
    public final ModConfigSpec.IntValue completedExitDelaySeconds;
    public final ModConfigSpec.IntValue forceCollapseTimeoutSeconds;
    public final ModConfigSpec.IntValue cleanupRetrySeconds;
    public final ModConfigSpec.IntValue defaultHorizontalPadding;
    public final ModConfigSpec.IntValue defaultVerticalPadding;
    public final ModConfigSpec.IntValue maximumTerrainRadius;
    public final ModConfigSpec.IntValue maximumConcurrentInstances;
    public final ModConfigSpec.IntValue approachDistance;
    public final ModConfigSpec.IntValue approachPlatformRadius;
    public final ModConfigSpec.IntValue approachPathWidth;
    public final ModConfigSpec.IntValue approachPathClearanceHeight;
    public final ModConfigSpec.IntValue approachPlatformClearanceHeight;
    public final ModConfigSpec.BooleanValue allowNaturalMobSpawning;
    public final ModConfigSpec.BooleanValue debugLogging;
    public final ModConfigSpec.ConfigValue<String> fallbackReturnDimension;
    public final ModConfigSpec.ConfigValue<List<? extends String>> structures;
    public final ModConfigSpec.ConfigValue<List<? extends String>> structureTags;
    public final ModConfigSpec.ConfigValue<List<? extends String>> poolItemOnlyStructureTags;
    public final ModConfigSpec.ConfigValue<List<? extends String>> excludedStructures;
    public final ModConfigSpec.ConfigValue<List<? extends String>> dungeonOverrides;
    public final ModConfigSpec.BooleanValue automaticRecipeGeneration;
    public final ModConfigSpec.BooleanValue paletteInference;
    public final ModConfigSpec.BooleanValue biomeInference;
    public final ModConfigSpec.BooleanValue dimensionInference;
    public final ModConfigSpec.BooleanValue nameInference;
    public final ModConfigSpec.BooleanValue rarityInference;
    public final ModConfigSpec.ConfigValue<List<? extends String>> excludedRecipeBlocks;
    public final ModConfigSpec.ConfigValue<List<? extends String>> excludedAutomaticRecipeTargets;
    public final ModConfigSpec.ConfigValue<String> approachPlatformBlock;
    public final ModConfigSpec.ConfigValue<String> approachPathBlock;
    public final ModConfigSpec.BooleanValue manifestationEnabled;
    public final ModConfigSpec.DoubleValue hologramMaxWidth;
    public final ModConfigSpec.DoubleValue hologramMaxHeight;
    public final ModConfigSpec.DoubleValue hologramMaxDepth;
    public final ModConfigSpec.EnumValue<AnimationMode> defaultAnimationMode;
    public final ModConfigSpec.ConfigValue<List<? extends String>> allowedRandomModes;
    public final ModConfigSpec.DoubleValue generationTimeBudgetMillis;
    public final ModConfigSpec.IntValue maximumBlockOperationsPerTick;
    public final ModConfigSpec.IntValue animationDurationMinimumTicks;
    public final ModConfigSpec.IntValue animationDurationMaximumTicks;
    public final ModConfigSpec.IntValue collapseDurationTicks;
    public final ModConfigSpec.IntValue portalGrowthDurationTicks;
    public final ModConfigSpec.IntValue portalCloseDurationTicks;
    public final ModConfigSpec.IntValue manifestationRenderDistance;
    public final ModConfigSpec.DoubleValue terrainAlpha;
    public final ModConfigSpec.DoubleValue structureAlpha;
    public final ModConfigSpec.EnumValue<PreparationParticleStyle> preparationParticleStyle;
    public final ModConfigSpec.ConfigValue<String> preparationParticleColor;
    public final ModConfigSpec.IntValue preparationParticleRate;
    public final ModConfigSpec.DoubleValue preparationParticleScale;
    public final ModConfigSpec.DoubleValue preparationParticleRadius;
    public final ModConfigSpec.IntValue iconResolution;
    public final ModConfigSpec.IntValue iconCacheLimit;
    public final ModConfigSpec.IntValue poolItemSwapIntervalSeconds;
    public final ModConfigSpec.IntValue maximumSnapshotBlocks;
    public final ModConfigSpec.DoubleValue portalWidth;
    public final ModConfigSpec.DoubleValue portalHeight;
    public final ModConfigSpec.DoubleValue portalDepth;
    public final ModConfigSpec.DoubleValue portalMinimumWidth;
    public final ModConfigSpec.DoubleValue portalMinimumHeight;
    public final ModConfigSpec.DoubleValue portalMinimumDepth;
    public final ModConfigSpec.ConfigValue<String> portalInnerColor;
    public final ModConfigSpec.ConfigValue<String> portalOuterColor;
    public final ModConfigSpec.BooleanValue derivePortalInnerColorFromBiome;
    public final ModConfigSpec.BooleanValue derivePortalOuterColorFromBiomeFog;
    public final ModConfigSpec.DoubleValue portalInnerTransparency;
    public final ModConfigSpec.DoubleValue portalInnerBiomeBrightness;
    public final ModConfigSpec.IntValue portalOuterOpacityPercent;
    public final ModConfigSpec.IntValue destinationPortalBehindEntryBlocks;
    public final ModConfigSpec.IntValue sourcePortalExitOffsetBlocks;
    public final ModConfigSpec.IntValue portalLifetimeMinutes;
    public final ModConfigSpec.IntValue portalHudDistance;
    public final ModConfigSpec.ConfigValue<String> portalCompletionOffering;
    public final ModConfigSpec.EnumValue<CatalystConsumptionPolicy> catalystConsumptionPolicy;
    public final ModConfigSpec.BooleanValue refundOnFailure;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        INSTANCE = new ServerConfig(builder);
        SPEC = builder.build();
    }

    private ServerConfig(ModConfigSpec.Builder builder) {
        builder.push("catalogue");
        structures = builder.comment(
                "Worldgen structure IDs automatically exposed as dungeon options.",
                "The automatic dungeon ID is the same as the structure ID. No structures are enabled by default.")
            .defineList("structures", ProductionConfigDefaults.STRUCTURES,
                () -> "namespace:structure", ServerConfig::isResourceId);
        structureTags = builder.comment(
                "Structure registry tags expanded once when the catalogue is rebuilt.",
                "Each listed tag also receives a named structure-pool manifestation catalyst.",
                "Write tag IDs without a leading #.")
            .defineList("structureTags", List.of(), () -> "namespace:instanced_dungeons", ServerConfig::isString);
        poolItemOnlyStructureTags = builder.comment(
                "Structure tags whose members are available only through the generated pool catalyst.",
                "A member still receives an exact catalyst when it is also listed explicitly in structures.",
                "Tags listed here are included even when omitted from structureTags; a leading # is optional.")
            .defineList("poolItemOnlyStructureTags", List.of(), () -> "namespace:rare", ServerConfig::isString);
        excludedStructures = builder.comment("Structure IDs removed after direct IDs and tags are combined.")
            .defineList("excludedStructures", List.of(), () -> "namespace:broken_structure", ServerConfig::isString);
        dungeonOverrides = builder.comment(
                "Partial automatic-definition overrides. Use semicolon-separated key=value fields.",
                "Example: minecraft:ancient_city;environment=CAVE;horizontal_padding=96;weight=2",
                "Supported keys: environment, custom_strategy, biomes, horizontal_padding, vertical_padding, maximum_radius,",
                "weight, placement, natural_mob_spawning, reentry, cost_tier, recipe_signature, recipe_theme,",
                "recipe_core, recipe_catalyst. Ingredient overrides accept an item ID or #item_tag.")
            .defineList("dungeonOverrides", List.of(),
                () -> "minecraft:ancient_city;environment=CAVE;horizontal_padding=96", ServerConfig::isString);
        builder.pop();

        builder.push("recipes");
        automaticRecipeGeneration = builder.comment(
                "Generate a normal shaped recipe for each configured exact-dungeon catalyst that has no datapack recipe.")
            .define("automaticRecipeGeneration", true);
        paletteInference = builder.define("paletteInference", true);
        biomeInference = builder.define("biomeInference", true);
        dimensionInference = builder.define("dimensionInference", true);
        nameInference = builder.define("nameInference", true);
        rarityInference = builder.define("rarityInference", true);
        excludedRecipeBlocks = builder.comment(
                "Blocks that automatic recipes must never use as ingredients.",
                "Entries may be exact block IDs or block tags prefixed with #.")
            .defineList("excludedRecipeBlocks", List.of(), () -> "#minecraft:logs", ServerConfig::isString);
        excludedAutomaticRecipeTargets = builder.comment(
                "Dungeon structures and structure pools that must not receive automatic recipes.",
                "Use an exact structure ID, or prefix a structure tag with # to exclude that pool and all of its members.",
                "Ordinary datapack recipes are never removed by this option.")
            .defineList("excludedAutomaticRecipeTargets", List.of(), () -> "#namespace:disabled_pool", ServerConfig::isString);
        builder.pop();

        builder.push("instances");
        vacancyTimeoutSeconds = builder.comment(
                "Seconds a never-entered instance remains open; 0 cleans it immediately and -1 keeps it open indefinitely.")
            .defineInRange("vacancyTimeoutSeconds", ProductionConfigDefaults.INSTANCE_OPEN_SECONDS,
                InstanceLifecycleSettings.INFINITE, InstanceLifecycleSettings.MAX_SECONDS);
        postVisitVacancyTimeoutSeconds = builder.comment(
                "Seconds an instance remains after every player leaves; 0 cleans it immediately and -1 keeps it open indefinitely.")
            .defineInRange("postVisitVacancyTimeoutSeconds", ProductionConfigDefaults.INSTANCE_POST_VISIT_SECONDS,
                InstanceLifecycleSettings.INFINITE, InstanceLifecycleSettings.MAX_SECONDS);
        forceCollapseTimeoutSeconds = builder.comment(
                "Absolute seconds from instance readiness until forced collapse, even while occupied.",
                "-1 disables forced collapse. When all three instance timeouts are -1, ordinary vacancy never closes an instance.")
            .defineInRange("forceCollapseTimeoutSeconds", ProductionConfigDefaults.INSTANCE_FORCE_COLLAPSE_SECONDS,
                InstanceLifecycleSettings.INFINITE, InstanceLifecycleSettings.MAX_SECONDS);
        completedExitDelaySeconds = builder.comment("Delay before completed instances return players and begin cleanup.")
            .defineInRange("completedExitDelaySeconds", 5, 0, 300);
        maximumConcurrentInstances = builder.comment("Maximum non-final dungeon instances on this server.")
            .defineInRange("maximumConcurrentInstances", 32, 1, 1024);
        builder.pop();

        builder.push("terrain");
        defaultHorizontalPadding = builder.defineInRange("defaultHorizontalPadding", 48, 0, 512);
        defaultVerticalPadding = builder.defineInRange("defaultVerticalPadding", 32, 0, 256);
        maximumTerrainRadius = builder.comment("Hard cap applied even when a definition requests a larger radius.")
            .defineInRange("maximumTerrainRadius", 256, 16, 1024);
        allowNaturalMobSpawning = builder.define("allowNaturalMobSpawning", true);
        builder.pop();

        builder.push("approach");
        approachDistance = builder.comment(
                "Distance from the detected front edge to the center of the automatic player platform.")
            .defineInRange("distance", 12, 2, 64);
        approachPlatformRadius = builder.comment("Platform radius; 2 creates a 5x5 platform.")
            .defineInRange("platformRadius", 2, 1, 8);
        approachPathWidth = builder.comment("Width of the path from the platform to the detected entrance; odd values center exactly.")
            .defineInRange("pathWidth", 3, 1, 9);
        approachPathClearanceHeight = builder.comment("Air blocks cleared above every bridge/path floor block.")
            .defineInRange("pathClearanceHeight", 3, 2, 16);
        approachPlatformClearanceHeight = builder.comment("Air blocks cleared above every arrival-platform floor block.")
            .defineInRange("platformClearanceHeight", 4, 2, 16);
        approachPlatformBlock = builder.comment("Block used for the automatic arrival platform.")
            .define("platformBlock", "minecraft:crying_obsidian", ServerConfig::isResourceId);
        approachPathBlock = builder.comment("Block used for the automatic path to the entrance.")
            .define("pathBlock", "minecraft:smooth_stone", ServerConfig::isResourceId);
        builder.pop();

        builder.push("safety");
        cleanupRetrySeconds = builder.comment("Minimum interval between retries for locked instance directories.")
            .defineInRange("cleanupRetrySeconds", 30, 1, 3600);
        fallbackReturnDimension = builder.comment("Fallback dimension used if a stored return dimension is unavailable.")
            .define("fallbackReturnDimension", "minecraft:overworld");
        debugLogging = builder.define("debugLogging", false);
        builder.pop();

        builder.push("manifestation");
        manifestationEnabled = builder.comment("Enable manifestation triggers, holograms, and portals.")
            .define("enabled", true);
        hologramMaxWidth = builder.defineInRange("hologramMaxWidth", 3.0, 0.5, 32.0);
        hologramMaxHeight = builder.defineInRange("hologramMaxHeight", 3.0, 0.5, 32.0);
        hologramMaxDepth = builder.defineInRange("hologramMaxDepth", 3.0, 0.5, 32.0);
        defaultAnimationMode = builder.defineEnum("defaultAnimationMode", AnimationMode.RANDOM_MODE);
        allowedRandomModes = builder.comment("Modes eligible when defaultAnimationMode is RANDOM_MODE.")
            .defineList("allowedRandomModes", List.of(
                "GROUND_UP", "MIDDLE_OUT", "OUTSIDE_IN", "SINGLE_ORIGIN", "MULTI_ORIGIN", "CHAOTIC"),
                () -> "GROUND_UP", value -> value instanceof String string && isAnimationMode(string));
        generationTimeBudgetMillis = builder.comment("Primary global server-thread generation budget per tick.")
            .defineInRange("generationTimeBudgetMillis", 4.0, 0.1, 25.0);
        maximumBlockOperationsPerTick = builder.comment("Secondary hard safety cap; time remains the primary limiter.")
            .defineInRange("maximumBlockOperationsPerTick", 5000, 1, 1_000_000);
        animationDurationMinimumTicks = builder.defineInRange("animationDurationMinimumTicks", 100, 1, 72_000);
        animationDurationMaximumTicks = builder.defineInRange("animationDurationMaximumTicks", 400, 1, 72_000);
        collapseDurationTicks = builder.defineInRange("collapseDurationTicks", 40, 1, 1200);
        portalGrowthDurationTicks = builder.comment("Ticks for the portal plane to grow from its center to full size.")
            .defineInRange("portalGrowthDurationTicks", 30, 1, 1200);
        portalCloseDurationTicks = builder.comment("Ticks for the portal's final dither dissolve before removal.")
            .defineInRange("portalCloseDurationTicks", 30, 1, 1200);
        manifestationRenderDistance = builder.defineInRange("renderDistance", 64, 8, 1024);
        terrainAlpha = builder.defineInRange("terrainAlpha", 0.35, 0.0, 1.0);
        structureAlpha = builder.defineInRange("structureAlpha", 1.0, 0.0, 1.0);
        preparationParticleStyle = builder.comment(
                "Immediate cue while terrain and heightmaps are prepared before the hologram begins.",
                "NONE disables it; RING, SPIRAL, and CONVERGING select the motion pattern.")
            .defineEnum("preparationParticleStyle", PreparationParticleStyle.SPIRAL);
        preparationParticleColor = builder.comment("Preparation particle color in #RRGGBB format.")
            .define("preparationParticleColor", "#2AAAFF",
                value -> value instanceof String color && ParticleColor.isValid(color));
        preparationParticleRate = builder.comment("Preparation particles emitted per client tick; 0 disables emission.")
            .defineInRange("preparationParticleRate", 3, 0, 64);
        preparationParticleScale = builder.defineInRange("preparationParticleScale", 0.7, 0.1, 4.0);
        preparationParticleRadius = builder.comment("Horizontal radius around the future hologram and portal.")
            .defineInRange("preparationParticleRadius", 1.75, 0.25, 16.0);
        maximumSnapshotBlocks = builder.comment("Hard structure-shell block cap for one miniature snapshot.")
            .defineInRange("maximumSnapshotBlocks", 50_000, 256, 500_000);
        iconResolution = builder.comment("Generated exact-dungeon icon resolution; high values use substantial GPU memory per cache entry.")
            .defineInRange("iconResolution", 256, 16, 2048);
        iconCacheLimit = builder.defineInRange("iconCacheLimit", 128, 1, 4096);
        poolItemSwapIntervalSeconds = builder.comment(
                "Seconds between structure-pool catalyst icon changes and between 3D miniature changes.")
            .defineInRange("poolItemSwapIntervalSeconds", ProductionConfigDefaults.POOL_ITEM_SWAP_INTERVAL_SECONDS, 1, 3600);
        portalWidth = builder.defineInRange("portalWidth", 1.5, 0.5, 8.0);
        portalHeight = builder.defineInRange("portalHeight", 2.5, 1.0, 8.0);
        portalDepth = builder.comment("Rendered portal volume depth; 0 keeps a flat plane.")
            .defineInRange("portalDepth", ProductionConfigDefaults.PORTAL_DEPTH, 0.0, 8.0);
        portalMinimumWidth = builder.comment(
                "Minimum visual portal width in blocks reached as the close timer expires.",
                "Values above portalWidth are clamped to the full portal width.")
            .defineInRange("portalMinimumWidth", ProductionConfigDefaults.PORTAL_MINIMUM_WIDTH, 0.1, 8.0);
        portalMinimumHeight = builder.comment(
                "Minimum visual portal height in blocks reached as the close timer expires.",
                "The bottom edge remains fixed; values above portalHeight are clamped to the full portal height.")
            .defineInRange("portalMinimumHeight", ProductionConfigDefaults.PORTAL_MINIMUM_HEIGHT, 0.1, 8.0);
        portalMinimumDepth = builder.comment(
                "Minimum visual portal depth in blocks reached as the close timer expires.",
                "Values above portalDepth are clamped to the full portal depth.")
            .defineInRange("portalMinimumDepth", ProductionConfigDefaults.PORTAL_MINIMUM_DEPTH, 0.0, 8.0);
        portalInnerColor = builder.comment("Portal interior in #RRGGBBAA format; the final byte is opacity.")
            .define("portalInnerColor", "#010104F5", value -> value instanceof String color && PortalColor.isValid(color));
        portalOuterColor = builder.comment("Portal glow in #RRGGBBAA format; the final byte is opacity.")
            .define("portalOuterColor", "#2AAAFF73", value -> value instanceof String color && PortalColor.isValid(color));
        derivePortalInnerColorFromBiome = builder.comment(
                "When no datapack inner color is present, derive its RGB from the selected dungeon biome's fog color.",
                "The global portalInnerColor remains the fallback when this is false.",
                "This supersedes the old opt-in key so existing installations receive the new default.")
            .define("derivePortalInnerColorFromBiome", true);
        derivePortalOuterColorFromBiomeFog = builder.comment(
                "When no datapack outer color is present, derive its RGB from the selected dungeon biome's fog color.",
                "The global portalOuterColor remains the fallback when this is false.")
            .define("derivePortalOuterColorFromBiomeFog", true);
        portalInnerTransparency = builder.comment(
                "Transparency applied to an automatically sampled inner color: 0 is opaque and 1 is invisible.",
                "Explicit datapack #RRGGBBAA values retain their own alpha.")
            .defineInRange("portalInnerTransparency", 0.04, 0.0, 1.0);
        portalInnerBiomeBrightness = builder.comment(
                "Relative brightness applied to the sampled biome color.",
                "-1 produces black, 0 preserves the biome color, and 1 produces white.")
            .defineInRange("portalInnerBiomeBrightness", 0.0, -1.0, 1.0);
        portalOuterOpacityPercent = builder.comment(
                "Opacity percentage applied to an automatically sampled outer color; explicit #RRGGBBAA values retain their own alpha.")
            .defineInRange("portalOuterOpacityPercent", 45, 0, 100);
        destinationPortalBehindEntryBlocks = builder.comment(
                "Distance behind the resolved player arrival point for the instance return portal.")
            .defineInRange("destinationPortalBehindEntryBlocks", 2, 1, 32);
        sourcePortalExitOffsetBlocks = builder.comment(
                "Distance from the source entrance portal where its return portal sends players.",
                "The side is chosen from the player's original approach so returning cannot immediately re-enter the dungeon.")
            .defineInRange("sourcePortalExitOffsetBlocks", 4, 1, 32);
        portalLifetimeMinutes = builder.comment("Maximum open time; 0 follows instance lifetime only.")
            .defineInRange("portalLifetimeMinutes", 0, 0, 1440);
        portalHudDistance = builder.comment(
                "Maximum distance in blocks for the dungeon icon, name, and portal-close countdown while aiming at a portal.")
            .defineInRange("portalHudDistance", 16, 1, 128);
        portalCompletionOffering = builder.comment(
                "Item ID or #item_tag consumed when thrown into either endpoint of an open portal.",
                "A matching offering marks that portal's exact dungeon instance complete.")
            .define("completionOffering", ProductionConfigDefaults.PORTAL_COMPLETION_OFFERING,
                value -> value instanceof String string && CompletionOfferingSelector.parse(string).isPresent());
        catalystConsumptionPolicy = builder.defineEnum("catalystConsumptionPolicy", CatalystConsumptionPolicy.ON_SUCCESS);
        refundOnFailure = builder.define("refundOnFailure", true);
        builder.pop();
    }

    private static boolean isString(Object value) {
        return value instanceof String;
    }

    private static boolean isResourceId(Object value) {
        return value instanceof String string && string.matches("[a-z0-9_.-]+:[a-z0-9/._-]+");
    }

    private static boolean isAnimationMode(String value) {
        try {
            AnimationMode mode = AnimationMode.parse(value);
            return mode != AnimationMode.RANDOM_MODE;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
