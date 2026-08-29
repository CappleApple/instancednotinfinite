package com.cappleapple.instancednotinfinite.gametest;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.backend.VanillaDynamicLevelBackend;
import com.cappleapple.instancednotinfinite.config.ProductionConfigDefaults;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.definition.BiomeRule;
import com.cappleapple.instancednotinfinite.definition.AutomaticDungeonMetadata;
import com.cappleapple.instancednotinfinite.definition.AutomaticDungeonResolver;
import com.cappleapple.instancednotinfinite.definition.DecorationMode;
import com.cappleapple.instancednotinfinite.definition.DefinitionResolver;
import com.cappleapple.instancednotinfinite.definition.DungeonDefinition;
import com.cappleapple.instancednotinfinite.definition.DungeonDefinitionRegistry;
import com.cappleapple.instancednotinfinite.definition.DungeonOverride;
import com.cappleapple.instancednotinfinite.definition.EntryPoint;
import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import com.cappleapple.instancednotinfinite.definition.HeightContext;
import com.cappleapple.instancednotinfinite.definition.PlacementMode;
import com.cappleapple.instancednotinfinite.definition.ReentryPolicy;
import com.cappleapple.instancednotinfinite.definition.ResolutionException;
import com.cappleapple.instancednotinfinite.definition.ResolvedDungeonDefinition;
import com.cappleapple.instancednotinfinite.definition.ResolvedDungeonOption;
import com.cappleapple.instancednotinfinite.definition.StructureKind;
import com.cappleapple.instancednotinfinite.definition.TerrainSettings;
import com.cappleapple.instancednotinfinite.instance.DungeonInstance;
import com.cappleapple.instancednotinfinite.instance.DungeonInstanceManager;
import com.cappleapple.instancednotinfinite.instance.DestinationPortalPlacement;
import com.cappleapple.instancednotinfinite.instance.InstanceOperationException;
import com.cappleapple.instancednotinfinite.api.DungeonManifestationApi;
import com.cappleapple.instancednotinfinite.api.ManifestationView;
import com.cappleapple.instancednotinfinite.content.ManifestationPortalBlockEntity;
import com.cappleapple.instancednotinfinite.content.ManifestationTargetComponent;
import com.cappleapple.instancednotinfinite.content.PortalCompletionOffering;
import com.cappleapple.instancednotinfinite.instance.InstanceLifecycleOverrides;
import com.cappleapple.instancednotinfinite.content.ModContent;
import com.cappleapple.instancednotinfinite.instance.InstanceState;
import com.cappleapple.instancednotinfinite.manifestation.AnimationMode;
import com.cappleapple.instancednotinfinite.manifestation.DungeonManifestationManager;
import com.cappleapple.instancednotinfinite.manifestation.DungeonTarget;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationOptions;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationState;
import com.cappleapple.instancednotinfinite.manifestation.PortalColor;
import com.cappleapple.instancednotinfinite.snapshot.VisualLayer;
import com.cappleapple.instancednotinfinite.recipe.PortalRecipeGenerationService;
import com.cappleapple.instancednotinfinite.recipe.RecipeSource;
import com.cappleapple.instancednotinfinite.recipe.RecipeIngredientExclusions;
import com.cappleapple.instancednotinfinite.structure.StructureFoundationAnalyzer;
import com.cappleapple.instancednotinfinite.terrain.FoundationSeatingReference;
import com.cappleapple.instancednotinfinite.terrain.DungeonChunkGenerator;
import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import com.cappleapple.instancednotinfinite.terrain.MaterialPalette;
import com.cappleapple.instancednotinfinite.terrain.TerrainEnvelopeStrategy;
import com.cappleapple.instancednotinfinite.terrain.TerrainStrategyRegistry;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(InstancedNotInfinite.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DungeonLifecycleGameTests {
    private static final String TEST_TEMPLATE = "bastion/mobs/empty";

    private DungeonLifecycleGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE)
    public static void vanillaPortalSoundDefaultsResolve(GameTestHelper helper) {
        for (String raw : List.of(
            ProductionConfigDefaults.GENERATION_SOUND,
            ProductionConfigDefaults.PORTAL_OPEN_SOUND,
            ProductionConfigDefaults.PORTAL_AMBIENT_SOUND,
            ProductionConfigDefaults.PORTAL_WALK_THROUGH_SOUND,
            ProductionConfigDefaults.PORTAL_CLOSING_SOUND,
            ProductionConfigDefaults.PORTAL_CLOSED_SOUND
        )) {
            ResourceLocation id = ResourceLocation.parse(raw);
            helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(id), "Missing vanilla sound event " + id);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE)
    public static void automaticPortalRecipeIsAStandardTargetedRecipe(GameTestHelper helper) {
        ResourceLocation dungeon = ResourceLocation.parse("instancednotinfinite:surface_igloo");
        var report = PortalRecipeGenerationService.INSTANCE.report(dungeon).orElse(null);
        helper.assertTrue(report != null, "Automatic portal recipe analysis was not cached");
        helper.assertValueEqual(report.source(), RecipeSource.AUTO_GENERATED,
            "Analyzable template dungeon did not use automatic recipe generation");
        var holder = helper.getLevel().getRecipeManager().byKey(report.recipeId().orElseThrow()).orElse(null);
        helper.assertTrue(holder != null, "Generated portal recipe is absent from Minecraft's recipe manager");
        var result = holder.value().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModContent.MANIFESTATION_CATALYST.get()),
            "Generated recipe does not output the manifestation catalyst");
        helper.assertValueEqual(result.get(ModContent.MANIFESTATION_TARGET.get()),
            ManifestationTargetComponent.dungeon(dungeon), "Generated result lost its exact dungeon component");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE)
    public static void explicitDatapackRecipeWinsWithoutAutomaticAnalysis(GameTestHelper helper) {
        ResourceLocation dungeon = ResourceLocation.parse("instancednotinfinite:cave_mineshaft");
        var service = PortalRecipeGenerationService.INSTANCE;
        var report = service.report(dungeon).orElse(null);
        helper.assertTrue(report != null, "Datapack portal recipe was not reported");
        helper.assertTrue(service.profile(dungeon).isEmpty(),
            "Predefined datapack recipe still triggered automatic structure analysis");
        helper.assertValueEqual(report.source(), RecipeSource.DATAPACK,
            "Explicit normal datapack recipe did not take precedence");
        helper.assertValueEqual(report.recipeId().orElseThrow(),
            ResourceLocation.parse("instancednotinfinite:explicit_cave_mineshaft_portal"),
            "Wrong explicit recipe was associated with the targeted catalyst");
        var holder = helper.getLevel().getRecipeManager().byKey(report.recipeId().orElseThrow()).orElseThrow();
        var result = holder.value().getResultItem(helper.getLevel().registryAccess());
        helper.assertValueEqual(result.get(ModContent.INSTANCE_LIFECYCLE.get()),
            InstanceLifecycleOverrides.of(900, 120, -1),
            "Explicit recipe result lost its instance lifecycle component");
        DungeonInstanceManager.get(helper.getLevel().getServer()).rebuildCatalogue();
        helper.assertTrue(service.profile(dungeon).isEmpty(),
            "Catalogue/config rebuild analyzed a target that still had a predefined datapack recipe");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE)
    public static void explicitPoolRecipeSkipsMemberAnalysis(GameTestHelper helper) {
        ResourceLocation poolId = ResourceLocation.parse("instancednotinfinite:predefined_recipe_test_pool");
        ResourceLocation igloo = ResourceLocation.withDefaultNamespace("igloo");
        ResourceLocation mineshaft = ResourceLocation.withDefaultNamespace("mineshaft");
        ResourceLocation predefinedRecipe = ResourceLocation.parse(
            "instancednotinfinite:explicit_recipe_test_pool_portal");
        DungeonInstanceManager instances = DungeonInstanceManager.get(helper.getLevel().getServer());
        try {
            ServerConfig.INSTANCE.structures.set(List.of());
            ServerConfig.INSTANCE.structureTags.set(List.of(poolId.toString()));
            ServerConfig.INSTANCE.poolItemOnlyStructureTags.set(List.of(poolId.toString()));
            ServerConfig.INSTANCE.excludedAutomaticRecipeTargets.set(List.of());
            instances.rebuildCatalogue();

            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(predefinedRecipe).isPresent(),
                "Predefined structure-pool recipe was removed during generated recipe installation");
            helper.assertTrue(helper.getLevel().getRecipeManager()
                    .byKey(PortalRecipeGenerationService.generatedPoolId(poolId)).isEmpty(),
                "Structure pool with a predefined recipe still received an automatic recipe");
            helper.assertTrue(PortalRecipeGenerationService.INSTANCE.profile(igloo).isEmpty(),
                "Pool-only igloo was analyzed even though the pool recipe was predefined");
            helper.assertTrue(PortalRecipeGenerationService.INSTANCE.profile(mineshaft).isEmpty(),
                "Pool-only mineshaft was analyzed even though the pool recipe was predefined");
        } finally {
            ServerConfig.INSTANCE.structures.set(List.of());
            ServerConfig.INSTANCE.structureTags.set(List.of());
            ServerConfig.INSTANCE.poolItemOnlyStructureTags.set(List.of());
            ServerConfig.INSTANCE.excludedAutomaticRecipeTargets.set(List.of());
            instances.rebuildCatalogue();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE)
    public static void structurePoolRecipesRespectPoolOnlyAndExclusionConfigs(GameTestHelper helper) {
        ResourceLocation poolId = ResourceLocation.parse("instancednotinfinite:recipe_test_pool");
        ResourceLocation igloo = ResourceLocation.withDefaultNamespace("igloo");
        ResourceLocation mineshaft = ResourceLocation.withDefaultNamespace("mineshaft");
        DungeonInstanceManager instances = DungeonInstanceManager.get(helper.getLevel().getServer());
        try {
            ServerConfig.INSTANCE.structures.set(List.of(igloo.toString()));
            ServerConfig.INSTANCE.structureTags.set(List.of(poolId.toString()));
            ServerConfig.INSTANCE.poolItemOnlyStructureTags.set(List.of(poolId.toString()));
            ServerConfig.INSTANCE.excludedRecipeBlocks.set(List.of("minecraft:snow_block", "#minecraft:logs"));
            ServerConfig.INSTANCE.excludedAutomaticRecipeTargets.set(List.of());
            instances.rebuildCatalogue();

            var registry = DungeonDefinitionRegistry.INSTANCE;
            helper.assertValueEqual(registry.structurePools().get(poolId), List.of(igloo, mineshaft),
                "Structure-tag pool did not retain its deterministic member list");
            helper.assertTrue(registry.exposedDungeonCatalystIds().contains(igloo),
                "Explicit structures entry did not override pool-only suppression");
            helper.assertFalse(registry.exposedDungeonCatalystIds().contains(mineshaft),
                "Tag-only structure still exposed an exact catalyst in pool-only mode");
            helper.assertTrue(registry.selectStructurePool(poolId, 1234L).filter(List.of(igloo, mineshaft)::contains).isPresent(),
                "Named structure pool could not select one of its members");

            var blockExclusions = RecipeIngredientExclusions.configured(helper.getLevel().registryAccess());
            helper.assertTrue(blockExclusions.excludesBlock(ResourceLocation.withDefaultNamespace("snow_block")),
                "Exact recipe block exclusion was not resolved");
            helper.assertTrue(blockExclusions.excludesBlock(ResourceLocation.withDefaultNamespace("oak_log")),
                "Recipe block-tag exclusion was not expanded");

            ResourceLocation poolRecipeId = PortalRecipeGenerationService.generatedPoolId(poolId);
            var poolRecipe = helper.getLevel().getRecipeManager().byKey(poolRecipeId).orElse(null);
            helper.assertTrue(poolRecipe != null, "Structure pool did not receive an automatic recipe");
            helper.assertValueEqual(
                poolRecipe.value().getResultItem(helper.getLevel().registryAccess()).get(ModContent.MANIFESTATION_TARGET.get()),
                ManifestationTargetComponent.structurePool(poolId),
                "Pool recipe result did not retain its structure-tag target");
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(
                    "instancednotinfinite:generated_portal/minecraft/igloo")).isPresent(),
                "Explicitly listed pool member lost its exact generated recipe");
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(
                    "instancednotinfinite:generated_portal/minecraft/mineshaft")).isEmpty(),
                "Pool-only member still received an exact generated recipe");

            ServerConfig.INSTANCE.excludedAutomaticRecipeTargets.set(List.of("#" + poolId));
            instances.rebuildCatalogue();
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(poolRecipeId).isEmpty(),
                "Excluded structure tag still received a pool recipe");
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(
                    "instancednotinfinite:generated_portal/minecraft/igloo")).isEmpty(),
                "Member of an excluded structure tag still received an exact automatic recipe");
        } finally {
            ServerConfig.INSTANCE.structures.set(List.of());
            ServerConfig.INSTANCE.structureTags.set(List.of());
            ServerConfig.INSTANCE.poolItemOnlyStructureTags.set(List.of());
            ServerConfig.INSTANCE.excludedRecipeBlocks.set(List.of());
            ServerConfig.INSTANCE.excludedAutomaticRecipeTargets.set(List.of());
            instances.rebuildCatalogue();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE, timeoutTicks = 1200)
    public static void manifestationGatesPortalAndEntersExactInstance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonManifestationManager manifestations = DungeonManifestationManager.get(level.getServer());
        DungeonInstanceManager instances = DungeonInstanceManager.get(level.getServer());
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        level.removeBlock(origin, false);
        level.removeBlock(origin.above(), false);
        ManifestationView view;
        try {
            view = DungeonManifestationApi.spawn(
                level, origin,
                DungeonTarget.dungeon(ResourceLocation.parse("instancednotinfinite:surface_igloo")),
                new ManifestationOptions(Direction.NORTH, AnimationMode.GROUND_UP), null);
            manifestations.finishAnimation(view.id());
        } catch (InstanceOperationException exception) {
            helper.fail("Manifestation API could not start: " + exception.getMessage());
            return;
        }

        DungeonInstance instance = instances.get(new com.cappleapple.instancednotinfinite.instance.InstanceId(view.instanceId())).orElseThrow();
        AtomicReference<ItemEntity> completionOffering = new AtomicReference<>();
        helper.assertValueEqual(instance.state(), InstanceState.CREATING,
            "Manifestation opened an immediately active instance instead of a resumable generation job");
        helper.assertFalse(level.getBlockState(origin).is(ModContent.MANIFESTATION_PORTAL.get()),
            "Portal appeared before instance generation completed");

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertValueEqual(
                manifestations.get(view.id()).orElseThrow().state(), ManifestationState.PORTAL_OPENING,
                "Waiting for the generation-gated portal growth phase"))
            .thenExecute(() -> {
                helper.assertTrue(instance.state() == InstanceState.ACTIVE || instance.state() == InstanceState.VACANT,
                    "Portal growth started without a ready ACTIVE/VACANT instance");
                helper.assertTrue(level.getBlockState(origin).is(ModContent.MANIFESTATION_PORTAL.get()),
                    "Manifestation did not place its compact portal block");
                helper.assertTrue(level.getBlockEntity(origin) instanceof ManifestationPortalBlockEntity portal
                        && portal.manifestationId().filter(view.id()::equals).isPresent(),
                    "Portal block entity was not bound to the manifestation UUID");
                var snapshot = manifestations.snapshot(view.id()).orElseThrow();
                helper.assertTrue(!snapshot.blocks().isEmpty(), "Completed manifestation retained no visual snapshot");
                helper.assertTrue(snapshot.blocks().stream().anyMatch(block -> block.layer() == VisualLayer.STRUCTURE),
                    "Snapshot did not include the generated structure");
                helper.assertTrue(snapshot.blocks().stream().noneMatch(block -> block.layer() == VisualLayer.TERRAIN),
                    "Structure-only snapshot still included the generated terrain envelope");
                ManifestationPortalBlockEntity resolvedPortal = (ManifestationPortalBlockEntity)level.getBlockEntity(origin);
                int biomeFogColor = level.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                    .getHolder(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.BIOME, instance.biomeId()))
                    .orElseThrow()
                    .value()
                    .getFogColor();
                helper.assertValueEqual(
                    resolvedPortal.portalInnerColor(),
                    PortalColor.withTransparency(
                        PortalColor.adjustBrightness(
                            biomeFogColor, ServerConfig.INSTANCE.portalInnerBiomeBrightness.get()),
                        ServerConfig.INSTANCE.portalInnerTransparency.get()),
                    "Automatically derived inner portal color did not use the selected biome fog RGB and configured shading");
                helper.assertValueEqual(
                    resolvedPortal.portalOuterColor(),
                    PortalColor.withOpacityPercent(
                        biomeFogColor, ServerConfig.INSTANCE.portalOuterOpacityPercent.get()),
                    "Automatically derived outer portal color did not use the selected biome fog RGB and configured opacity");
                ServerPlayer earlyPlayer = mockServerPlayer(level.getServer(), level, "ini-test-growing-portal");
                try {
                    earlyPlayer.teleportTo(
                        level, origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5,
                        earlyPlayer.getYRot(), earlyPlayer.getXRot());
                    level.getBlockState(origin).entityInside(level, origin, earlyPlayer);
                    helper.assertTrue(earlyPlayer.level() == level,
                        "Growing portal allowed entry before reaching full size");
                } finally {
                    level.getServer().getPlayerList().remove(earlyPlayer);
                }
            })
            .thenWaitUntil(() -> helper.assertValueEqual(
                manifestations.get(view.id()).orElseThrow().state(), ManifestationState.PORTAL_OPEN,
                "Waiting for the portal to finish growing"))
            .thenExecute(() -> {
                ServerPlayer portalPlayer = mockServerPlayer(level.getServer(), level, "ini-test-portal");
                try {
                    int sourceOffset = ServerConfig.INSTANCE.sourcePortalExitOffsetBlocks.get();
                    BlockPos expectedSourceReturn = origin.relative(Direction.NORTH, sourceOffset);
                    level.setBlock(expectedSourceReturn.below(), Blocks.STONE.defaultBlockState(), 3);
                    level.removeBlock(expectedSourceReturn, false);
                    level.removeBlock(expectedSourceReturn.above(), false);
                    portalPlayer.teleportTo(
                        level, origin.getX() - 0.4, origin.getY(), origin.getZ() + 0.5,
                        portalPlayer.getYRot(), portalPlayer.getXRot());
                    ManifestationPortalBlockEntity sourcePortal = (ManifestationPortalBlockEntity)level.getBlockEntity(origin);
                    helper.assertTrue(sourcePortal.interactionBounds().intersects(portalPlayer.getBoundingBox()),
                        "Rendered portal edge was outside the portal interaction volume");
                    helper.assertFalse(new net.minecraft.world.phys.AABB(origin).intersects(portalPlayer.getBoundingBox()),
                        "Portal hitbox test player still overlapped the single anchor block");
                    manifestations.tryActivatePortal(portalPlayer);
                    helper.assertTrue(portalPlayer.level().dimension().location().equals(instance.dimensionId()),
                        "UUID-bound portal did not enter its exact generated instance");
                    var plan = instance.plan().orElseThrow();
                    BlockPos returnPortalPos = DestinationPortalPlacement.position(
                        plan, ServerConfig.INSTANCE.destinationPortalBehindEntryBlocks.get());
                    ServerLevel instanceLevel = portalPlayer.serverLevel();
                    helper.assertTrue(instanceLevel.getBlockState(returnPortalPos).is(ModContent.MANIFESTATION_PORTAL.get()),
                        "Destination instance did not place a return portal behind its arrival point");
                    helper.assertTrue(instanceLevel.getBlockEntity(returnPortalPos) instanceof ManifestationPortalBlockEntity returnPortal
                            && returnPortal.endpoint() == ManifestationPortalBlockEntity.Endpoint.RETURN
                            && returnPortal.instanceId().filter(view.instanceId()::equals).isPresent(),
                        "Destination return portal was not bound to the exact instance UUID");
                    ManifestationPortalBlockEntity returnPortal = (ManifestationPortalBlockEntity)instanceLevel.getBlockEntity(returnPortalPos);
                    helper.assertTrue(returnPortal.dungeonId().filter(view.dungeonId()::equals).isPresent(),
                        "Destination return portal did not retain the dungeon ID needed for its client HUD icon");
                    helper.assertValueEqual(returnPortal.portalInnerColor(), sourcePortal.portalInnerColor(),
                        "Destination portal did not reuse the source portal inner color");
                    helper.assertValueEqual(returnPortal.portalOuterColor(), sourcePortal.portalOuterColor(),
                        "Destination portal did not reuse the source portal outer color");
                    portalPlayer.setPortalCooldown(0);
                    portalPlayer.teleportTo(
                        instanceLevel, returnPortalPos.getX() + 0.5, returnPortalPos.getY(), returnPortalPos.getZ() + 0.5,
                        portalPlayer.getYRot(), portalPlayer.getXRot());
                    instanceLevel.getBlockState(returnPortalPos).entityInside(instanceLevel, returnPortalPos, portalPlayer);
                    helper.assertTrue(portalPlayer.level() == level,
                        "Destination return portal did not return the player to the saved origin dimension");
                    helper.assertTrue(portalPlayer.blockPosition().equals(expectedSourceReturn),
                        "Destination return portal did not honor the configured source-side exit offset");
                } finally {
                    level.getServer().getPlayerList().remove(portalPlayer);
                }
            })
            .thenExecute(() -> {
                helper.assertTrue(PortalCompletionOffering.matches(
                        new ItemStack(Items.BLAZE_POWDER), "#instancednotinfinite:completion_offering_test"),
                    "Configured completion item tags did not resolve against the live item registry");
                ManifestationPortalBlockEntity sourcePortal =
                    (ManifestationPortalBlockEntity)level.getBlockEntity(origin);
                var bounds = sourcePortal.interactionBounds();
                ItemEntity offering = new ItemEntity(
                    level, bounds.minX + 0.1, origin.getY() + 1.0, (bounds.minZ + bounds.maxZ) * 0.5,
                    new ItemStack(Items.BLAZE_POWDER, 2));
                offering.setNoGravity(true);
                helper.assertFalse(new net.minecraft.world.phys.AABB(origin).intersects(offering.getBoundingBox()),
                    "Completion offering test still overlapped the single portal anchor block");
                helper.assertTrue(sourcePortal.intersects(offering.getBoundingBox()),
                    "Completion offering was outside the rendered portal interaction volume");
                completionOffering.set(offering);
                level.addFreshEntity(offering);
            })
            .thenWaitUntil(() -> helper.assertValueEqual(
                instance.state(), InstanceState.COMPLETED,
                "Waiting for the thrown completion offering to complete the exact portal instance"))
            .thenExecute(() -> {
                ItemEntity offering = completionOffering.get();
                helper.assertTrue(offering != null && offering.isAlive(),
                    "Portal consumed the entire completion-offering stack instead of one item");
                helper.assertValueEqual(offering.getItem().getCount(), 1,
                    "Portal did not consume exactly one completion offering");
            })
            .thenExecute(() -> {
                try {
                    manifestations.cancel(view.id(), "GameTest cleanup after offering completion assertion");
                    helper.assertValueEqual(
                        manifestations.get(view.id()).orElseThrow().state(), ManifestationState.CLOSING,
                        "Cancellation skipped the visible portal closing phase");
                    helper.assertTrue(level.getBlockState(origin).is(ModContent.MANIFESTATION_PORTAL.get()),
                        "Closing portal block disappeared before its reverse animation completed");
                } catch (InstanceOperationException exception) {
                    helper.fail("Manifestation cleanup failed: " + exception.getMessage());
                }
            })
            .thenWaitUntil(() -> helper.assertValueEqual(
                manifestations.get(view.id()).orElseThrow().state(), ManifestationState.CANCELLED,
                "Waiting for the portal closing animation to finish"))
            .thenExecute(() -> helper.assertFalse(level.getBlockState(origin).is(ModContent.MANIFESTATION_PORTAL.get()),
                "Portal block remained after its closing animation completed"))
            .thenWaitUntil(() -> helper.assertTrue(instances.get(instance.id()).isEmpty(),
                "Waiting for manifestation instance cleanup"))
            .thenSucceed();
    }

    @GameTest(
        templateNamespace = "minecraft",
        template = TEST_TEMPLATE,
        timeoutTicks = 200
    )
    public static void jigsawFoundationPlacementPropertiesCanBeInspected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
            .get(ResourceLocation.withDefaultNamespace("pillager_outpost"));
        helper.assertTrue(structure != null, "Vanilla pillager outpost structure was not registered");
        StructureStart start = structure.generate(
            level.registryAccess(), level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(),
            level.getChunkSource().randomState(), level.getStructureManager(), 19L, new ChunkPos(0, 0), 0, level, holder -> true);
        helper.assertTrue(start.isValid(), "Vanilla jigsaw structure did not produce a test start");
        StructureFoundationAnalyzer.FoundationProfile profile = StructureFoundationAnalyzer.profile(level, start).orElse(null);
        helper.assertTrue(profile != null, "Jigsaw template geometry did not produce a dominant foundation");
        helper.assertTrue(profile.foundation().baseY() >= start.getBoundingBox().minY()
                && profile.foundation().baseY() <= start.getBoundingBox().maxY(),
            "Inferred jigsaw foundation was outside the generated structure bounds");
        int seatingReference = FoundationSeatingReference.select(
            profile.foundation(), profile.placementGroundY(), structure.terrainAdaptation() != TerrainAdjustment.NONE);
        helper.assertTrue(seatingReference >= profile.foundation().baseY(),
            "Jigsaw placement properties produced a seating level below the foundation");
        helper.succeed();
    }

    @GameTest(
        templateNamespace = "minecraft",
        template = TEST_TEMPLATE,
        timeoutTicks = 200
    )
    public static void playerRoundTripAndCleanup(GameTestHelper helper) {
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        ServerPlayer firstPlayer = mockServerPlayer(helper.getLevel().getServer(), helper.getLevel(), "ini-test-first");
        ServerPlayer secondPlayer = mockServerPlayer(helper.getLevel().getServer(), helper.getLevel(), "ini-test-second");
        Level originalLevel = firstPlayer.level();
        DungeonInstance instance;
        try {
            instance = manager.create(ResourceLocation.fromNamespaceAndPath(InstancedNotInfinite.MOD_ID, "surface_igloo"));
            manager.enter(firstPlayer, instance.id());
            manager.enter(secondPlayer, instance.id());
            helper.assertTrue(firstPlayer.level().dimension().location().equals(instance.dimensionId()),
                "First mock server player did not enter the runtime dungeon level");
            helper.assertTrue(firstPlayer.level() == secondPlayer.level(),
                "Two assigned players did not share the same instance level");
            helper.assertValueEqual(instance.assignedPlayers().size(), 2,
                "Instance did not retain both assigned player UUIDs");
            helper.assertTrue(manager.leave(firstPlayer), "First dungeon leave did not restore the player");
            helper.assertTrue(manager.leave(secondPlayer), "Second dungeon leave did not restore the player");
            helper.assertTrue(firstPlayer.level() == originalLevel && secondPlayer.level() == originalLevel,
                "Players did not return to the originating level");

            manager.enter(firstPlayer, instance.id());
            helper.assertTrue(manager.leave(firstPlayer), "Re-entry while active did not return the player");
            manager.delete(instance.id());
        } catch (InstanceOperationException exception) {
            helper.fail("Dungeon lifecycle failed: " + exception.getMessage());
            return;
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(firstPlayer);
            helper.getLevel().getServer().getPlayerList().remove(secondPlayer);
        }

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(
                helper.getLevel().getServer().getLevel(VanillaDynamicLevelBackend.levelKey(instance.id())) == null,
                "Waiting for the runtime dimension to detach live during pending deletion"))
            .thenWaitUntil(() -> helper.assertTrue(manager.get(instance.id()).isEmpty(),
                "Waiting for deferred unload and filesystem cleanup"))
            .thenSucceed();
    }

    @GameTest(
        templateNamespace = "minecraft",
        template = TEST_TEMPLATE,
        timeoutTicks = 240
    )
    public static void highYPositionDoesNotSnapBackToEntry(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        DungeonInstanceManager manager = DungeonInstanceManager.get(server);
        ServerPlayer player = mockServerPlayer(server, helper.getLevel(), "ini-test-radius");
        DungeonInstance instance;
        double targetX;
        double targetZ;
        try {
            instance = manager.create(ResourceLocation.fromNamespaceAndPath(InstancedNotInfinite.MOD_ID, "surface_igloo"));
            manager.enter(player, instance.id());
            BlockPos entry = instance.plan().orElseThrow().entryPosition();
            targetX = entry.getX() + 4.5;
            targetZ = entry.getZ() + 4.5;
            player.setNoGravity(true);
            player.teleportTo((ServerLevel)player.level(), targetX, entry.getY() + 16.0,
                targetZ, player.getYRot(), player.getXRot());
        } catch (InstanceOperationException exception) {
            server.getPlayerList().remove(player);
            helper.fail("Radius regression setup failed: " + exception.getMessage());
            return;
        }

        helper.startSequence()
            .thenIdle(45)
            .thenExecute(() -> {
                try {
                    helper.assertTrue(player.level().dimension().location().equals(instance.dimensionId()),
                        "Player unexpectedly left the runtime dungeon level");
                    helper.assertTrue(Math.abs(player.getX() - targetX) < 0.01,
                        "High dungeon Y position triggered a snapback on the X axis");
                    helper.assertTrue(Math.abs(player.getZ() - targetZ) < 0.01,
                        "High dungeon Y position triggered a snapback on the Z axis");
                    helper.assertTrue(manager.leave(player), "Radius regression player could not leave the dungeon");
                    manager.delete(instance.id());
                } catch (InstanceOperationException exception) {
                    helper.fail("Radius regression cleanup failed: " + exception.getMessage());
                } finally {
                    server.getPlayerList().remove(player);
                }
            })
            .thenIdle(20)
            .thenWaitUntil(() -> helper.assertTrue(manager.get(instance.id()).isEmpty(),
                "Waiting for radius regression instance cleanup"))
            .thenSucceed();
    }

    @GameTest(
        templateNamespace = "minecraft",
        template = TEST_TEMPLATE,
        timeoutTicks = 240
    )
    public static void runtimeLevelIsAddedToServerTickLoop(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        DungeonInstanceManager manager = DungeonInstanceManager.get(server);
        ServerPlayer player = mockServerPlayer(server, helper.getLevel(), "ini-test-world-tick");
        DungeonInstance instance;
        ServerLevel dungeon;
        try {
            instance = manager.create(ResourceLocation.fromNamespaceAndPath(InstancedNotInfinite.MOD_ID, "surface_igloo"));
            manager.enter(player, instance.id());
            dungeon = player.serverLevel();
        } catch (InstanceOperationException exception) {
            server.getPlayerList().remove(player);
            helper.fail("Runtime level ticking setup failed: " + exception.getMessage());
            return;
        }

        helper.startSequence()
            .thenIdle(20)
            .thenExecute(() -> {
                try {
                    helper.assertTrue(server.getTickTime(dungeon.dimension()) != null,
                        "Runtime dungeon level was absent from Minecraft's world tick loop");
                    helper.assertTrue(manager.leave(player), "Runtime ticking test player could not leave the dungeon");
                    manager.delete(instance.id());
                } catch (InstanceOperationException exception) {
                    helper.fail("Runtime ticking cleanup failed: " + exception.getMessage());
                } finally {
                    server.getPlayerList().remove(player);
                }
            })
            .thenIdle(20)
            .thenWaitUntil(() -> helper.assertTrue(manager.get(instance.id()).isEmpty(),
                "Waiting for runtime ticking instance cleanup"))
            .thenSucceed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE)
    public static void definitionResolutionIsGraceful(GameTestHelper helper) {
        try {
            ResolvedDungeonDefinition resolved = DefinitionResolver.resolve(
                helper.getLevel().registryAccess(), helper.getLevel().getStructureManager(),
                definition(EnvironmentType.SURFACE, "minecraft:igloo/top", "#minecraft:is_overworld"), 42L);
            helper.assertTrue(resolved.structureKind() == StructureKind.TEMPLATE,
                "Known NBT template did not resolve as TEMPLATE");
            helper.assertTrue(resolved.biome().isBound(), "Biome tag did not resolve to a bound biome holder");
        } catch (ResolutionException exception) {
            helper.fail("Valid registry-backed definition did not resolve: " + exception.getMessage());
            return;
        }

        assertResolutionFails(helper,
            definition(EnvironmentType.SURFACE, "instancednotinfinite:missing_structure", "minecraft:plains"),
            "missing structure");
        assertResolutionFails(helper,
            definition(EnvironmentType.SURFACE, "minecraft:igloo/top", "instancednotinfinite:missing_biome"),
            "missing biome");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE, timeoutTicks = 300)
    public static void automaticCatalogueUsesStructureRegistryMetadata(GameTestHelper helper) {
        ResourceLocation igloo = ResourceLocation.withDefaultNamespace("igloo");
        ServerConfig.INSTANCE.structures.set(List.of(igloo.toString()));
        DungeonInstanceManager.get(helper.getLevel().getServer()).rebuildCatalogue();
        DungeonDefinition definition = DungeonDefinitionRegistry.INSTANCE.get(igloo).orElse(null);
        AutomaticDungeonMetadata metadata = DungeonDefinitionRegistry.INSTANCE.inspect(igloo).orElse(null);
        helper.assertTrue(definition != null, "Configured minecraft:igloo did not become an automatic dungeon option");
        helper.assertTrue(metadata != null, "Automatic igloo option did not retain inference diagnostics");
        helper.assertTrue(definition.structureKind() == StructureKind.WORLDGEN,
            "Automatic structure did not resolve as a worldgen structure");
        helper.assertTrue(definition.structure().equals("minecraft:igloo"),
            "Automatic dungeon option did not retain its structure registry key");
        helper.assertTrue(metadata.resolvedBiomeCount() > 0,
            "Automatic structure biome HolderSet resolved to no candidate biomes");
        try {
            ResolvedDungeonOption overridden = AutomaticDungeonResolver.resolve(
                helper.getLevel().registryAccess(), igloo, List.of("test"),
                new DungeonOverride(null, null, List.of(new BiomeRule("minecraft:plains", 1)),
                    80, null, null, 3, null, null, null, null, null, null, null, null),
                48, 32, 256);
            helper.assertValueEqual(overridden.metadata().resolvedBiomeCount(), 1,
                "Explicit biome override did not replace the automatic HolderSet");
            helper.assertValueEqual(overridden.definition().terrain().horizontalPadding(), 80,
                "Partial padding override was not applied");
            helper.assertValueEqual(overridden.definition().weight(), 3,
                "Partial selection-weight override was not applied");
        } catch (ResolutionException exception) {
            helper.fail("Valid automatic metadata override did not resolve: " + exception.getMessage());
            return;
        }

        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        DungeonInstance instance;
        try {
            instance = manager.create(igloo);
            helper.assertTrue(instance.plan().isPresent(),
                "Automatic structure instance did not retain its measured generation plan");
            GenerationPlan automaticPlan = instance.plan().orElseThrow();
            assertPlanPadding(helper, automaticPlan);
            ServerLevel instanceLevel = helper.getLevel().getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, instance.dimensionId()));
            helper.assertTrue(instanceLevel != null
                    && instanceLevel.getChunkSource().getGenerator() instanceof DungeonChunkGenerator,
                "Standard automatic instance did not use the controlled finite terrain generator");
            DungeonChunkGenerator generator = (DungeonChunkGenerator)instanceLevel.getChunkSource().getGenerator();
            helper.assertFalse(generator.usesCustomTerrain(),
                "Standard automatic instance unexpectedly selected a custom terrain strategy");
            helper.assertTrue(generator.usesSyntheticTerrain(),
                "Standard automatic instance unexpectedly retained vanilla terrain generation");
            int outsideX = automaticPlan.envelopeBounds().maxX() + 32;
            int outsideZ = automaticPlan.envelopeBounds().maxZ() + 32;
            helper.assertValueEqual(
                generator.getBaseHeight(
                    outsideX, outsideZ, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                    instanceLevel, instanceLevel.getChunkSource().randomState()),
                instanceLevel.getMinBuildHeight(),
                "Controlled terrain continued generating beyond the resolved instance envelope");
            int solidFalloffColumns = 0;
            int voidFalloffColumns = 0;
            int centerZ = (automaticPlan.guaranteedBounds().minZ() + automaticPlan.guaranteedBounds().maxZ()) / 2;
            for (int x = automaticPlan.guaranteedBounds().maxX() + 1;
                 x < automaticPlan.envelopeBounds().maxX(); x++) {
                for (int z = centerZ - 12; z <= centerZ + 12; z++) {
                    int height = generator.getBaseHeight(
                        x, z, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                        instanceLevel, instanceLevel.getChunkSource().randomState());
                    if (height == instanceLevel.getMinBuildHeight()) voidFalloffColumns++;
                    else solidFalloffColumns++;
                }
            }
            helper.assertTrue(solidFalloffColumns > 0 && voidFalloffColumns > 0,
                "Controlled terrain edge did not form a dithered solid-to-void gradient");
            int borderRadius = Math.max(
                Math.max(Math.abs(automaticPlan.envelopeBounds().minX()), Math.abs(automaticPlan.envelopeBounds().maxX())),
                Math.max(Math.abs(automaticPlan.envelopeBounds().minZ()), Math.abs(automaticPlan.envelopeBounds().maxZ())));
            helper.assertTrue(Math.abs(instanceLevel.getWorldBorder().getSize() - (borderRadius + 1) * 2.0) < 0.001,
                "Runtime world border still used the broad configured cap instead of the resolved instance envelope");
            BlockPos automaticEntry = automaticPlan.entryPosition();
            helper.assertTrue(automaticPlan.terrainSurfaceY() >= helper.getLevel().getMinBuildHeight()
                    && automaticPlan.terrainSurfaceY() < helper.getLevel().getMaxBuildHeight(),
                "Automatic worldgen structure did not retain its controlled generation surface");
            helper.assertTrue(Math.abs(automaticEntry.getY() - (automaticPlan.terrainSurfaceY() + 1)) <= 8,
                "Automatic surface entry was placed above or below the retained terrain surface");
            helper.assertTrue(automaticEntry.getX() < automaticPlan.structureBounds().minX()
                    || automaticEntry.getX() > automaticPlan.structureBounds().maxX()
                    || automaticEntry.getZ() < automaticPlan.structureBounds().minZ()
                    || automaticEntry.getZ() > automaticPlan.structureBounds().maxZ(),
                "Automatic surface entry was placed inside the structure instead of at an exterior approach");
            manager.delete(instance.id());
        } catch (InstanceOperationException exception) {
            helper.fail("Automatic structure option could not create an instance: " + exception.getMessage());
            return;
        }
        helper.startSequence()
            .thenIdle(20)
            .thenWaitUntil(() -> helper.assertTrue(manager.get(instance.id()).isEmpty(),
                "Automatic structure instance did not finish cleanup"))
            .thenSucceed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE)
    public static void terrainEnvelopesAreFinite(GameTestHelper helper) {
        BoundingBox structure = new BoundingBox(-5, 128, -5, 5, 140, 5);
        BlockPos origin = new BlockPos(-5, 128, -5);

        GenerationPlan surface = GenerationPlan.fromBounds(
            1L, definition(EnvironmentType.SURFACE, "minecraft:igloo/top", "minecraft:plains"), structure, origin);
        TerrainEnvelopeStrategy surfaceStrategy = TerrainStrategyRegistry.forEnvironment(EnvironmentType.SURFACE);
        helper.assertFalse(surfaceStrategy.blockAt(surface, MaterialPalette.forDefinition(surface.definition()), 0, 127, 0).isAir(),
            "Grounded surface did not provide terrain beneath the structure");
        helper.assertFalse(surfaceStrategy.blockAt(
                surface, MaterialPalette.forDefinition(surface.definition()), 0, GenerationPlan.MIN_TERRAIN_Y, 0).isAir(),
            "SURFACE terrain remained a floating island instead of reaching the instance floor");
        helper.assertValueEqual(surface.envelopeBounds().minY(), GenerationPlan.MIN_TERRAIN_Y,
            "SURFACE envelope was not anchored to the instance floor");
        helper.assertTrue(surfaceStrategy.blockAt(surface, MaterialPalette.forDefinition(surface.definition()), 500, 128, 500).isAir(),
            "Surface terrain escaped its finite envelope");
        helper.assertValueEqual(surface.guaranteedBounds().minX(), structure.minX() - 24,
            "Guaranteed horizontal padding was not derived from structure bounds");
        helper.assertTrue(surface.envelopeBounds().minX() < surface.guaranteedBounds().minX(),
            "Surface terrain has no outer falloff band");
        int middleFalloffX = (surface.envelopeBounds().minX() + surface.guaranteedBounds().minX()) / 2;
        boolean foundVerticalDither = false;
        for (int x = middleFalloffX - 2; x <= middleFalloffX + 2 && !foundVerticalDither; x++) {
            for (int z = -4; z <= 4 && !foundVerticalDither; z++) {
                boolean solid = false;
                boolean air = false;
                for (int y = 88; y <= 104; y++) {
                    if (surfaceStrategy.blockAt(surface, MaterialPalette.forDefinition(surface.definition()), x, y, z).isAir()) {
                        air = true;
                    } else {
                        solid = true;
                    }
                }
                foundVerticalDither = solid && air;
            }
        }
        helper.assertTrue(foundVerticalDither,
            "Surface falloff still dissolved as whole vertical columns instead of a three-dimensional dither");

        BoundingBox basementStructure = new BoundingBox(-8, 78, -8, 8, 142, 8);
        GenerationPlan seatedSurface = GenerationPlan.fromBounds(
            11L, definition(EnvironmentType.SURFACE, "minecraft:igloo/top", "minecraft:plains"),
            basementStructure, new BlockPos(-8, 78, -8), true, GenerationPlan.ANCHOR_Y - 1);
        TerrainEnvelopeStrategy seatedStrategy = TerrainStrategyRegistry.forEnvironment(EnvironmentType.SURFACE);
        MaterialPalette seatedPalette = MaterialPalette.forDefinition(seatedSurface.definition());
        helper.assertFalse(seatedStrategy.blockAt(
                seatedSurface, seatedPalette, 0, GenerationPlan.ANCHOR_Y - 1, 0).isAir(),
            "Surface terrain followed the structure's deepest foundation instead of its generation surface");
        helper.assertTrue(seatedStrategy.blockAt(
                seatedSurface, seatedPalette, 0, GenerationPlan.ANCHOR_Y, 0).isAir(),
            "Surface terrain rose above the retained structure placement surface");

        GenerationPlan netherSurface = GenerationPlan.fromBounds(
            12L, definition(EnvironmentType.NETHER_LIKE, "minecraft:igloo/top", "minecraft:nether_wastes"),
            basementStructure, new BlockPos(-8, 78, -8), true, GenerationPlan.ANCHOR_Y - 1);
        TerrainEnvelopeStrategy netherStrategy = TerrainStrategyRegistry.forEnvironment(EnvironmentType.NETHER_LIKE);
        helper.assertFalse(netherStrategy.blockAt(
                netherSurface, MaterialPalette.forDefinition(netherSurface.definition()),
                0, GenerationPlan.MIN_TERRAIN_Y, 0).isAir(),
            "NETHER_LIKE surface structure still used a floating enclosed blob");

        GenerationPlan oceanSurface = GenerationPlan.fromBounds(
            13L, definition(EnvironmentType.OCEAN_SURFACE, "minecraft:igloo/top", "minecraft:deep_ocean"),
            structure, origin, true, 130);
        TerrainEnvelopeStrategy oceanStrategy = TerrainStrategyRegistry.forEnvironment(EnvironmentType.OCEAN_SURFACE);
        MaterialPalette oceanPalette = MaterialPalette.forDefinition(oceanSurface.definition());
        helper.assertTrue(oceanStrategy.blockAt(oceanSurface, oceanPalette, 0, 130, 0).is(Blocks.WATER),
            "OCEAN_SURFACE did not retain water at the authored world-surface height");
        helper.assertTrue(oceanStrategy.blockAt(oceanSurface, oceanPalette, 0, 131, 0).isAir(),
            "OCEAN_SURFACE submerged the space above its authored waterline");
        helper.assertTrue(oceanStrategy.blockAt(oceanSurface, oceanPalette, 0, 118, 0).is(Blocks.SAND),
            "OCEAN_SURFACE did not provide a finite ocean floor below the waterline");

        GenerationPlan floating = GenerationPlan.fromBounds(
            2L, definition(EnvironmentType.FLOATING_ISLAND, "minecraft:igloo/top", "minecraft:plains"), structure, origin);
        TerrainEnvelopeStrategy floatingStrategy = TerrainStrategyRegistry.forEnvironment(EnvironmentType.FLOATING_ISLAND);
        helper.assertTrue(floatingStrategy.blockAt(
                floating, MaterialPalette.forDefinition(floating.definition()), 0, floating.envelopeBounds().minY(), 0).isAir(),
            "FLOATING_ISLAND no longer retained its intentional air gap");

        DungeonDefinition elongatedDefinition = new DungeonDefinition(
            "instancednotinfinite:elongated", 1, "minecraft:igloo/top", StructureKind.TEMPLATE, 1,
            List.of(new BiomeRule("minecraft:plains", 1)), new HeightContext(64, 120), EnvironmentType.SURFACE, null,
            new TerrainSettings(24, 16, 256), com.cappleapple.instancednotinfinite.definition.PortalSettings.DEFAULT,
            new EntryPoint(0, 1, 0, 0.0F, 0.0F),
            PlacementMode.DIRECT, DecorationMode.NONE, true, ReentryPolicy.WHILE_ACTIVE);
        BoundingBox elongatedBounds = new BoundingBox(-120, 128, -20, 120, 150, 20);
        GenerationPlan elongated = GenerationPlan.fromBounds(
            3L, elongatedDefinition, elongatedBounds, new BlockPos(-120, 128, -20));
        TerrainEnvelopeStrategy elongatedStrategy = TerrainStrategyRegistry.forEnvironment(EnvironmentType.SURFACE);
        MaterialPalette elongatedPalette = MaterialPalette.forDefinition(elongatedDefinition);
        helper.assertFalse(elongatedStrategy.blockAt(elongated, elongatedPalette, 110, 127, 0).isAir(),
            "Long structure footprint lost terrain support near its end");
        helper.assertTrue(elongatedStrategy.blockAt(elongated, elongatedPalette, 0, 127, 110).isAir(),
            "Long narrow structure collapsed into a largest-dimension circular island");

        assertEnclosedShape(helper, structure, origin, EnvironmentType.UNDERGROUND);
        assertEnclosedShape(helper, structure, origin, EnvironmentType.CAVE);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE, timeoutTicks = 300)
    public static void biomePaletteAndSafeSurfaceDecorationReachGeneratedTerrain(GameTestHelper helper) {
        var biomeRegistry = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        var desert = biomeRegistry.getHolder(ResourceKey.create(
            Registries.BIOME, ResourceLocation.withDefaultNamespace("desert"))).orElseThrow();
        MaterialPalette desertPalette = MaterialPalette.forDefinition(
            definition(EnvironmentType.SURFACE, "minecraft:desert_pyramid", "minecraft:desert"), desert);
        helper.assertTrue(desertPalette.surface().is(Blocks.SAND),
            "Desert biome did not select sand as its controlled terrain surface");

        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        DungeonInstance instance;
        try {
            instance = manager.create(ResourceLocation.fromNamespaceAndPath(InstancedNotInfinite.MOD_ID, "surface_igloo"));
            GenerationPlan plan = instance.plan().orElseThrow();
            ServerLevel dungeon = helper.getLevel().getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, instance.dimensionId()));
            helper.assertTrue(dungeon != null, "Biome surface test instance level was unavailable");

            BoundingBox bounds = plan.structureBounds();
            boolean foundGrassSurface = false;
            boolean foundSnowDecoration = false;
            int minChunkX = Math.floorDiv(bounds.minX(), 16);
            int maxChunkX = Math.floorDiv(bounds.maxX(), 16);
            int minChunkZ = Math.floorDiv(bounds.minZ(), 16);
            int maxChunkZ = Math.floorDiv(bounds.maxZ(), 16);
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
                        for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
                            if (x >= bounds.minX() && x <= bounds.maxX()
                                && z >= bounds.minZ() && z <= bounds.maxZ()) {
                                continue;
                            }
                            int topY = dungeon.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                            BlockPos top = new BlockPos(x, topY, z);
                            if (dungeon.getBlockState(top).is(Blocks.GRASS_BLOCK)) {
                                foundGrassSurface = true;
                            } else if (dungeon.getBlockState(top).is(Blocks.SNOW)
                                && dungeon.getBlockState(top.below()).is(Blocks.GRASS_BLOCK)) {
                                foundGrassSurface = true;
                                foundSnowDecoration = true;
                            }
                        }
                    }
                }
            }
            helper.assertTrue(foundGrassSurface,
                "Snowy selected biome did not use grass as its actual controlled surface block");
            helper.assertTrue(foundSnowDecoration,
                "SAFE decoration did not run the snowy biome's top-layer snow feature");
            manager.delete(instance.id());
        } catch (InstanceOperationException exception) {
            helper.fail("Biome surface generation test failed: " + exception.getMessage());
            return;
        }

        helper.startSequence()
            .thenIdle(20)
            .thenWaitUntil(() -> helper.assertTrue(manager.get(instance.id()).isEmpty(),
                "Waiting for biome surface test instance cleanup"))
            .thenSucceed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE, timeoutTicks = 300)
    public static void simultaneousInstancesAreIsolated(GameTestHelper helper) {
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        DungeonInstance first;
        DungeonInstance second;
        try {
            ResourceLocation dungeon = ResourceLocation.fromNamespaceAndPath(InstancedNotInfinite.MOD_ID, "surface_igloo");
            first = manager.create(dungeon);
            second = manager.create(dungeon);
            helper.assertFalse(first.id().equals(second.id()), "Two instances received the same UUID");
            helper.assertFalse(first.dimensionId().equals(second.dimensionId()), "Two instances shared one dimension key");
            helper.assertFalse(first.seed() == second.seed(), "Two UUID instances derived the same seed");
            manager.delete(first.id());
            manager.delete(second.id());
        } catch (InstanceOperationException exception) {
            helper.fail("Simultaneous instance test failed: " + exception.getMessage());
            return;
        }

        helper.startSequence()
            .thenIdle(20)
            .thenWaitUntil(() -> helper.assertTrue(
                manager.get(first.id()).isEmpty() && manager.get(second.id()).isEmpty(),
                "Waiting for isolated instance cleanup"))
            .thenSucceed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE, timeoutTicks = 600)
    public static void variableWorldgenInstancesUseTheirOwnBounds(GameTestHelper helper) {
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        DungeonInstance first;
        DungeonInstance second;
        try {
            ResourceLocation dungeon = ResourceLocation.fromNamespaceAndPath(InstancedNotInfinite.MOD_ID, "cave_mineshaft");
            first = manager.create(dungeon);
            second = manager.create(dungeon);
            GenerationPlan firstPlan = first.plan().orElseThrow();
            GenerationPlan secondPlan = second.plan().orElseThrow();
            helper.assertFalse(sameBox(firstPlan.structureBounds(), secondPlan.structureBounds()),
                "Two seed-varying mineshaft instances unexpectedly reused one global bounding box");
            assertPlanPadding(helper, firstPlan);
            assertPlanPadding(helper, secondPlan);
            manager.delete(first.id());
            manager.delete(second.id());
        } catch (InstanceOperationException exception) {
            helper.fail("Variable structure sizing test failed: " + exception.getMessage());
            return;
        }

        helper.startSequence()
            .thenIdle(20)
            .thenWaitUntil(() -> helper.assertTrue(
                manager.get(first.id()).isEmpty() && manager.get(second.id()).isEmpty(),
                "Waiting for variable-size instance cleanup"))
            .thenSucceed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE, timeoutTicks = 240)
    public static void reconnectRecoversReturnLocation(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        DungeonInstanceManager manager = DungeonInstanceManager.get(server);
        ServerPlayer originalPlayer = mockServerPlayer(server, helper.getLevel(), "ini-test-reconnect");
        GameProfile profile = originalPlayer.getGameProfile();
        DungeonInstance instance;
        ServerPlayer reconnected = null;
        try {
            instance = manager.create(ResourceLocation.fromNamespaceAndPath(InstancedNotInfinite.MOD_ID, "surface_igloo"));
            manager.enter(originalPlayer, instance.id());
            helper.assertTrue(originalPlayer.level().dimension().location().equals(instance.dimensionId()),
                "Player was not inside the instance before disconnect");

            server.getPlayerList().remove(originalPlayer);
            reconnected = reconnect(server, helper.getLevel(), profile);
            helper.assertTrue(reconnected.level() == helper.getLevel(),
                "Login recovery did not return the player from the temporary dimension");

            manager.enter(reconnected, instance.id());
            helper.assertTrue(manager.leave(reconnected), "Recovered player could not re-enter and leave the active instance");
            manager.delete(instance.id());
        } catch (InstanceOperationException exception) {
            helper.fail("Reconnect recovery failed: " + exception.getMessage());
            return;
        } finally {
            if (server.getPlayerList().getPlayer(profile.getId()) == originalPlayer) {
                server.getPlayerList().remove(originalPlayer);
            }
            if (reconnected != null && server.getPlayerList().getPlayer(profile.getId()) == reconnected) {
                server.getPlayerList().remove(reconnected);
            }
        }

        helper.startSequence()
            .thenIdle(20)
            .thenWaitUntil(() -> helper.assertTrue(manager.get(instance.id()).isEmpty(),
                "Waiting for reconnected instance cleanup"))
            .thenSucceed();
    }

    private static void assertResolutionFails(GameTestHelper helper, DungeonDefinition definition, String scenario) {
        try {
            DefinitionResolver.resolve(
                helper.getLevel().registryAccess(), helper.getLevel().getStructureManager(), definition, 11L);
            helper.fail("Definition resolution unexpectedly accepted " + scenario);
        } catch (ResolutionException expected) {
            // Expected: each invalid definition is isolated as a useful creation error.
        }
    }

    private static ServerPlayer reconnect(MinecraftServer server, ServerLevel initialLevel, GameProfile profile) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, initialLevel, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static ServerPlayer mockServerPlayer(MinecraftServer server, ServerLevel level, String name) {
        return reconnect(server, level, new GameProfile(UUID.randomUUID(), name));
    }

    private static void assertEnclosedShape(
        GameTestHelper helper,
        BoundingBox structure,
        BlockPos origin,
        EnvironmentType environment
    ) {
        DungeonDefinition definition = definition(environment, "minecraft:igloo/top", "minecraft:plains");
        GenerationPlan plan = GenerationPlan.fromBounds(9L, definition, structure, origin);
        TerrainEnvelopeStrategy strategy = TerrainStrategyRegistry.forEnvironment(environment);
        MaterialPalette palette = MaterialPalette.forDefinition(definition);
        helper.assertFalse(strategy.blockAt(plan, palette, 0, 134, 0).isAir(),
            environment + " did not fully encase the structure before authored pieces are placed");
        helper.assertFalse(strategy.blockAt(plan, palette, 20, 134, 0).isAir(),
            environment + " did not retain a surrounding terrain wall");
        helper.assertTrue(strategy.blockAt(plan, palette, 500, 134, 500).isAir(),
            environment + " terrain escaped its finite envelope");
    }

    private static void assertPlanPadding(GameTestHelper helper, GenerationPlan plan) {
        int horizontal = plan.definition().terrain().horizontalPadding();
        int vertical = plan.definition().terrain().verticalPadding();
        helper.assertValueEqual(plan.guaranteedBounds().minX(), plan.structureBounds().minX() - horizontal,
            "Variable structure minimum X padding was not derived from its actual bounds");
        helper.assertValueEqual(plan.guaranteedBounds().maxZ(), plan.structureBounds().maxZ() + horizontal,
            "Variable structure maximum Z padding was not derived from its actual bounds");
        helper.assertValueEqual(plan.guaranteedBounds().minY(),
            Math.max(GenerationPlan.MIN_TERRAIN_Y, plan.structureBounds().minY() - vertical),
            "Variable structure minimum Y padding was not derived from its actual bounds or clamped to build height");
        helper.assertValueEqual(plan.guaranteedBounds().maxY(),
            Math.min(GenerationPlan.MAX_TERRAIN_Y, plan.structureBounds().maxY() + vertical),
            "Variable structure maximum Y padding was not derived from its actual bounds or clamped to build height");
    }

    private static boolean sameBox(BoundingBox first, BoundingBox second) {
        return first.minX() == second.minX() && first.minY() == second.minY() && first.minZ() == second.minZ()
            && first.maxX() == second.maxX() && first.maxY() == second.maxY() && first.maxZ() == second.maxZ();
    }

    private static DungeonDefinition definition(
        EnvironmentType environment,
        String structure,
        String biome
    ) {
        return new DungeonDefinition(
            "instancednotinfinite:gametest", 1, structure, StructureKind.TEMPLATE, 1,
            List.of(new BiomeRule(biome, 1)), new HeightContext(-40, 0), environment, null,
            new TerrainSettings(24, 16, 96), com.cappleapple.instancednotinfinite.definition.PortalSettings.DEFAULT,
            new EntryPoint(0, 1, 0, 0.0F, 0.0F),
            PlacementMode.DIRECT, DecorationMode.SAFE, true, ReentryPolicy.WHILE_ACTIVE);
    }
}
