package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.content.ManifestationPortalBlockEntity;
import com.cappleapple.instancednotinfinite.definition.*;
import com.cappleapple.instancednotinfinite.terrain.*;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(InstancedNotInfinite.MOD_ID)
public final class AncientCityGameTests {
    private static final ResourceLocation CITY = ResourceLocation.parse("minecraft:ancient_city");

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("ancient_city_geometry", "instancednotinfinite.ancient_city_geometry",
                "instancednotinfinite_integration:empty", 400, 0L, true, AncientCityGameTests::geometry),
            new TestFunction("ancient_city_reported_floor", "instancednotinfinite.ancient_city_reported_floor",
                "instancednotinfinite_integration:empty", 2000, 0L, true, AncientCityGameTests::reportedLayoutFoundation),
            cityTest("regression", "c8eca1ce-d8ea-47df-918c-ce6b6ecddcd1"),
            cityTest("second_layout", "598da6be-9298-4b9e-b027-2c3662a0d292"));
    }

    private static TestFunction cityTest(String name, String uuid) {
        return new TestFunction("ancient_city_" + name, "instancednotinfinite.ancient_city_" + name,
            "instancednotinfinite_integration:empty", 200_000, 0L, true, helper -> city(helper, UUID.fromString(uuid)));
    }

    private static DungeonDefinition definition(GameTestHelper helper) {
        try {
            // Deliberately zero padding: the city must still retain its floor, roof, and enclosing walls.
            return AutomaticDungeonResolver.resolve(helper.getLevel().registryAccess(), CITY, List.of("gametest"),
                null, 0, 0, 512).definition();
        } catch (ResolutionException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void geometry(GameTestHelper helper) {
        DungeonDefinition definition = definition(helper);
        BoundingBox bounds = new BoundingBox(-100, 80, -70, 100, 135, 70);
        var plan = GenerationPlan.fromBounds(73L, definition, bounds, new BlockPos(-100, 80, -70), true, 90);
        helper.assertTrue(plan.ancientCityCavern(), "Ancient City did not select cavern terrain");
        helper.assertFalse(AncientCityTerrainStrategy.appliesTo(definition.withEnvironment(EnvironmentType.SURFACE)),
            "Explicit non-underground terrain was overridden");
        var template = new DungeonDefinition(definition.id(), 1, definition.structure(), StructureKind.TEMPLATE,
            1, definition.biomes(), definition.height(), EnvironmentType.CAVE, null, definition.terrain(), definition.portal(),
            definition.entry(), definition.placement(), definition.decoration(), true, definition.reentry());
        helper.assertFalse(AncientCityTerrainStrategy.appliesTo(template), "Template with the same ID was treated as the worldgen city");
        var strategy = TerrainStrategyRegistry.forPlan(plan);
        var palette = MaterialPalette.forDefinition(definition);
        int sculk = 0;
        for (int x = bounds.minX(); x <= bounds.maxX(); x += 5) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z += 5) {
                var column = strategy.column(plan, palette, x, z);
                for (int y = plan.terrainSurfaceY() + 1; y <= bounds.maxY() + 4; y++) {
                    helper.assertTrue(column.apply(y).isAir(), "Rock buries city or its required headroom at " + new BlockPos(x, y, z));
                }
                helper.assertFalse(column.apply(plan.terrainSurfaceY()).isAir(), "Noise left a gap beneath the city foundation");
                helper.assertTrue(column.apply(plan.terrainSurfaceY() - 5).is(Blocks.DEEPSLATE), "Cave floor lacks solid support");
                helper.assertTrue(column.apply(plan.guaranteedBounds().maxY()).is(Blocks.DEEPSLATE), "Cave roof opens into void");
                for (int y = plan.terrainSurfaceY() - 3; y <= plan.terrainSurfaceY(); y++) {
                    if (column.apply(y).is(Blocks.SCULK)) sculk++;
                }
            }
        }
        helper.assertTrue(sculk > 0, "Deep Dark cavern has no sculk floor patches");
        for (int y = plan.guaranteedBounds().minY(); y <= plan.guaranteedBounds().maxY(); y++) {
            helper.assertTrue(strategy.blockAt(plan, palette, plan.guaranteedBounds().minX(), y, 0).is(Blocks.DEEPSLATE),
                "Cavern perforates its enclosing wall");
        }
        helper.assertTrue(strategy.blockAt(plan, palette, plan.envelopeBounds().maxX() + 1, 100, 0).isAir(), "Terrain escapes finite envelope");
        helper.assertFalse(TerrainStrategyRegistry.forEnvironment(EnvironmentType.CAVE).blockAt(plan, palette, 0, 120, 0).isAir(),
            "Generic cave strategy was hollowed");
        var instance = new DungeonInstance(InstanceId.random(), definition, ResourceLocation.parse("instancednotinfinite:test_city"),
            CITY, StructureKind.WORLDGEN, Biomes.DEEP_DARK.location(), plan.seed(), 0L);
        instance.setPlan(plan);
        var saved = instance.save();
        var restored = DungeonInstance.load(saved).orElseThrow().plan().orElseThrow();
        helper.assertValueEqual(restored, plan, "Cavern geometry changed across save/load");
        saved.getCompound("Plan").remove("AncientCityCavern");
        var legacy = DungeonInstance.load(saved).orElseThrow().plan().orElseThrow();
        helper.assertFalse(legacy.ancientCityCavern(), "Existing saved terrain was silently replaced");
        helper.assertFalse(TerrainStrategyRegistry.forPlan(legacy).blockAt(legacy, palette, 0, 120, 0).isAir(), "Legacy saved city lost its terrain model");
        helper.succeed();
    }

    private static void reportedLayoutFoundation(GameTestHelper helper) {
        long seed = -3399384581734563066L; // Actual city shown floating in the user's 2026-09-09 log.
        try {
            var definition = definition(helper);
            var level = helper.getLevel();
            var resolved = DefinitionResolver.resolve(level.registryAccess(), level.getStructureManager(), definition, seed);
            var noise = level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS)
                .getHolderOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
            var generator = new DungeonChunkGenerator(resolved.biome(), noise, GenerationPlan.fallback(seed, definition));
            var prepared = new com.cappleapple.instancednotinfinite.structure.DungeonStructurePlacer()
                .prepare(level, resolved, generator, seed, true);
            var plan = GenerationPlan.fromBounds(seed, definition, prepared.bounds(), prepared.origin(), true, prepared.terrainSurfaceY());
            generator.updatePlan(plan);
            var profile = com.cappleapple.instancednotinfinite.structure.StructureFoundationAnalyzer
                .profile(level, prepared.worldgenStart()).orElseThrow();
            helper.assertValueEqual(plan.terrainSurfaceY(), profile.foundation().baseY() - 1,
                "Cave floor is not immediately under the measured foundation");
            var strategy = TerrainStrategyRegistry.forPlan(plan);
            var palette = MaterialPalette.forDefinition(definition);
            for (int x = plan.structureBounds().minX(); x <= plan.structureBounds().maxX(); x++) {
                for (int z = plan.structureBounds().minZ(); z <= plan.structureBounds().maxZ(); z++) {
                    helper.assertFalse(strategy.blockAt(plan, palette, x, plan.terrainSurfaceY(), z).isAir(),
                        "Reported city has unsupported foundation at " + new BlockPos(x, plan.terrainSurfaceY(), z));
                }
            }
            var sampled = generator.getBaseColumn(0, 0, level, level.getChunkSource().randomState());
            helper.assertFalse(sampled.getBlock(plan.terrainSurfaceY()).isAir(), "Column sampling lost foundation support");
            helper.assertTrue(sampled.getBlock(plan.terrainSurfaceY() + 1).isAir(), "Floor rise buries the city");
            helper.succeed();
        } catch (ResolutionException | com.cappleapple.instancednotinfinite.structure.PlacementException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void city(GameTestHelper helper, UUID id) {
        var manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        var previous = List.copyOf(ServerConfig.INSTANCE.structures.get());
        DungeonGenerationJob job;
        try {
            ServerConfig.INSTANCE.structures.set(List.of(CITY.toString()));
            manager.rebuildCatalogue();
            var method = DungeonInstanceManager.class.getDeclaredMethod("prepareCreation", ResourceLocation.class,
                InstanceId.class, InstanceLifecycleOverrides.class);
            method.setAccessible(true);
            var pending = (PendingDungeonCreation) method.invoke(manager, CITY, new InstanceId(id), InstanceLifecycleOverrides.empty());
            job = new DungeonGenerationJob(manager, pending, 1, false, ignored -> {});
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        } finally {
            ServerConfig.INSTANCE.structures.set(previous);
            manager.rebuildCatalogue();
        }
        helper.startSequence().thenWaitUntil(() -> {
            try {
                manager.advanceGeneration(job);
            } catch (InstanceOperationException exception) {
                throw new IllegalStateException(exception);
            }
            helper.assertTrue(job.complete(), "Waiting for full vanilla Ancient City");
        }).thenExecute(() -> {
            try {
                var instance = job.instance();
                var plan = instance.plan().orElseThrow();
                var level = helper.getLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, instance.dimensionId()));
                helper.assertTrue(instance.state() == InstanceState.ACTIVE || instance.state() == InstanceState.VACANT, "City failed readiness");
                helper.assertTrue(plan.ancientCityCavern(), "Safe-entry finalization lost cavern flag");
                helper.assertTrue(level.getBiome(plan.entryPosition()).is(Biomes.DEEP_DARK), "Ancient City cavern is not Deep Dark");
                helper.assertTrue(level.getBlockState(plan.entryPosition()).isAir(), "Arrival is obstructed");
                helper.assertTrue(level.getBlockState(plan.entryPosition().above()).isAir(), "Arrival headroom is obstructed");
                helper.assertFalse(level.getBlockState(plan.entryPosition().below()).isAir(), "Arrival has no floor");
                var portal = DestinationPortalPlacement.position(plan, ServerConfig.INSTANCE.destinationPortalBehindEntryBlocks.get());
                helper.assertTrue(level.getBlockEntity(portal) instanceof ManifestationPortalBlockEntity, "Return portal is missing");
                var bounds = plan.structureBounds();
                int air = 0;
                for (int x = bounds.minX(); x <= bounds.maxX(); x += 8) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z += 8) {
                        BlockPos overCity = new BlockPos(x, bounds.maxY() + 2, z);
                        helper.assertTrue(level.getBlockState(overCity).isAir(), "Terrain still buries the city at " + overCity);
                        helper.assertFalse(level.canSeeSky(overCity), "City cavern has no enclosing roof");
                        if (level.getBlockState(new BlockPos(x, plan.terrainSurfaceY() + 5, z)).isAir()) air++;
                    }
                }
                helper.assertTrue(air > 100, "City floor has insufficient open cave space");
                int foundations = 0;
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        BlockPos foundation = new BlockPos(x, plan.terrainSurfaceY() + 1, z);
                        var state = level.getBlockState(foundation);
                        if (state.is(Blocks.DEEPSLATE_BRICKS) || state.is(Blocks.DEEPSLATE_TILES)
                            || state.is(Blocks.CRACKED_DEEPSLATE_BRICKS) || state.is(Blocks.CRACKED_DEEPSLATE_TILES)) {
                            foundations++;
                            helper.assertFalse(level.getBlockState(foundation.below()).isAir(),
                                "Placed city foundation is floating at " + foundation);
                        }
                    }
                }
                helper.assertTrue(foundations > 100, "Test did not inspect substantial authored city foundations");
                InstancedNotInfinite.LOGGER.info("Ancient City supported foundations checked: {}", foundations);
                helper.assertValueEqual(DungeonInstance.load(instance.save()).orElseThrow().plan().orElseThrow(), plan,
                    "Ready city did not retain cavern geometry in saved instance");
                InstancedNotInfinite.LOGGER.info("Ancient City cavern validated: seed={}, bounds={}, floor={}, entry={}, open samples={}",
                    instance.seed(), bounds, plan.terrainSurfaceY(), plan.entryPosition(), air);
                manager.delete(instance.id());
            } catch (InstanceOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }).thenWaitUntil(() -> helper.assertTrue(manager.get(job.instance().id()).isEmpty(), "Waiting for city cleanup")).thenSucceed();
    }
}
