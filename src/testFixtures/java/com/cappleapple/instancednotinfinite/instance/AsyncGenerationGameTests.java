package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.content.ManifestationPortalBlockEntity;
import com.cappleapple.instancednotinfinite.definition.DefinitionParser;
import com.cappleapple.instancednotinfinite.gametest.PlacementTestStructures;
import com.cappleapple.instancednotinfinite.gametest.PlacementTestStructures.PreparationGate;
import com.cappleapple.instancednotinfinite.terrain.CustomTerrainStrategies;
import com.cappleapple.instancednotinfinite.terrain.DungeonChunkGenerator;
import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import com.google.gson.JsonParser;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(InstancedNotInfinite.MOD_ID)
public final class AsyncGenerationGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        // Separate batches keep deliberate worker pauses away from legacy synchronous API tests.
        return List.of(test("large_layout_yields", AsyncGenerationGameTests::largeLayout),
            test("cancel_running_layout", AsyncGenerationGameTests::cancelLayout),
            test("layout_failure_cleanup", AsyncGenerationGameTests::failedLayout),
            test("terrain_worker_snapshot", AsyncGenerationGameTests::terrainWorker),
            test("cancel_terrain_requests", AsyncGenerationGameTests::cancelTerrain));
    }

    private static TestFunction test(String name, Consumer<GameTestHelper> body) {
        return new TestFunction("async_" + name, "instancednotinfinite." + name,
            "instancednotinfinite_integration:empty", name.equals("large_layout_yields") ? 100_000 : 2400, 0L, true, body);
    }

    private static DungeonGenerationJob begin(GameTestHelper helper, String mode) {
        var manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        var previous = List.copyOf(ServerConfig.INSTANCE.structures.get());
        ResourceLocation id = ResourceLocation.parse("instancednotinfinite:placement_" + mode);
        try {
            ServerConfig.INSTANCE.structures.set(List.of(id.toString()));
            manager.rebuildCatalogue();
            return manager.beginGeneration(id, ignored -> {});
        } catch (InstanceOperationException exception) {
            throw new IllegalStateException(exception);
        } finally {
            ServerConfig.INSTANCE.structures.set(previous);
            manager.rebuildCatalogue();
        }
    }

    private static void advance(DungeonGenerationJob job) {
        try {
            DungeonInstanceManager.current().orElseThrow().advanceGeneration(job);
        } catch (InstanceOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void delete(DungeonGenerationJob job) {
        try {
            DungeonInstanceManager.current().orElseThrow().delete(job.instance().id());
        } catch (InstanceOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void cleaned(GameTestHelper helper, DungeonGenerationJob job) {
        helper.assertTrue(DungeonInstanceManager.current().orElseThrow().get(job.instance().id()).isEmpty(), "Waiting for asynchronous cleanup");
    }

    private static void largeLayout(GameTestHelper helper) {
        PreparationGate gate = new PreparationGate();
        PlacementTestStructures.preparationGate = gate;
        long queuedAt = helper.getLevel().getServer().getTickCount();
        DungeonGenerationJob job = begin(helper, "async_large");
        helper.assertFalse(job.prepared(), "Layout search finished inside beginGeneration");
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(gate.started.getCount() == 0, "Waiting for background layout"))
            .thenIdle(5)
            .thenExecute(() -> {
                advance(job);
                helper.assertFalse(job.prepared(), "Unfinished worker result was accepted");
                helper.assertValueEqual(job.progress(), 0.0, "Progress advanced before preparation completed");
                helper.assertTrue(helper.getLevel().getServer().getTickCount() > queuedAt, "Server did not tick while layout was blocked");
                gate.release.countDown();
            })
            .thenWaitUntil(() -> {
                advance(job);
                if ((helper.getLevel().getServer().getTickCount() - queuedAt) % 2000 == 0) {
                    InstancedNotInfinite.LOGGER.info("Async large dungeon progress {} after {} accelerated test ticks", job.progress(),
                        helper.getLevel().getServer().getTickCount() - queuedAt);
                }
                helper.assertTrue(job.complete(), "Waiting for large island and structure");
            })
            .thenExecute(() -> {
                helper.assertValueEqual(gate.attempts.get(), 9, "Compatible-start search skipped candidates");
                helper.assertTrue(gate.placedOnServer, "Structure was not applied on the server thread");
                helper.assertValueEqual(job.instance().state(), InstanceState.ACTIVE, "Instance activated before finishing");
                var plan = job.instance().plan().orElseThrow();
                helper.assertValueEqual(plan.structureBounds().getXSpan(), 129, "Large layout bounds were lost");
                var level = helper.getLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, job.instance().dimensionId()));
                var portal = DestinationPortalPlacement.position(plan, ServerConfig.INSTANCE.destinationPortalBehindEntryBlocks.get());
                helper.assertTrue(level.getBlockEntity(portal) instanceof ManifestationPortalBlockEntity, "Return portal is missing");
                helper.assertTrue(job.snapshot().orElseThrow().blocks().size() > 0, "Deferred preparation lost the hologram");
                InstancedNotInfinite.LOGGER.info("Async 129x129 dungeon completed after {} ticks; search attempts={}",
                    helper.getLevel().getServer().getTickCount() - queuedAt, gate.attempts.get());
                delete(job);
            })
            .thenWaitUntil(() -> cleaned(helper, job)).thenSucceed();
    }

    private static void cancelLayout(GameTestHelper helper) {
        PreparationGate gate = new PreparationGate();
        PlacementTestStructures.preparationGate = gate;
        DungeonGenerationJob job = begin(helper, "async_cancel");
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(gate.started.getCount() == 0, "Waiting for blocked layout"))
            .thenExecute(() -> delete(job))
            .thenWaitUntil(() -> helper.assertTrue(gate.interrupted, "Cancellation did not interrupt preparation"))
            .thenWaitUntil(() -> cleaned(helper, job))
            .thenExecute(() -> {
                helper.assertFalse(job.prepared(), "Cancelled layout was installed late");
                helper.assertFalse(job.complete(), "Cancelled instance became ready");
                helper.assertFalse(gate.placedOnServer, "Cancelled worker placed blocks");
            }).thenSucceed();
    }

    private static void failedLayout(GameTestHelper helper) {
        PreparationGate gate = new PreparationGate();
        gate.release.countDown();
        PlacementTestStructures.preparationGate = gate;
        DungeonGenerationJob job = begin(helper, "async_failure");
        helper.startSequence().thenWaitUntil(() -> {
            try {
                DungeonInstanceManager.current().orElseThrow().advanceGeneration(job);
            } catch (InstanceOperationException expected) {
                helper.assertTrue(expected.getMessage().contains("Expected asynchronous layout failure"), "Worker failure lost its cause");
                return;
            }
            helper.fail("Waiting for asynchronous failure");
        }).thenWaitUntil(() -> cleaned(helper, job)).thenSucceed();
    }

    private static void cancelTerrain(GameTestHelper helper) {
        PreparationGate gate = new PreparationGate();
        gate.release.countDown();
        PlacementTestStructures.preparationGate = gate;
        DungeonGenerationJob job = begin(helper, "async_cancel");
        helper.startSequence().thenWaitUntil(() -> {
            advance(job);
            helper.assertTrue(job.prepared(), "Waiting for preparation");
        }).thenIdle(1).thenExecute(() -> {
            try {
                // One operation submits a request and returns without consuming the future.
                job.advance(4.0, 1);
            } catch (InstanceOperationException exception) {
                throw new IllegalStateException(exception);
            }
            delete(job);
            job.releaseTickets();
            helper.assertFalse(job.complete(), "Cancelling terrain activated the dungeon");
        }).thenWaitUntil(() -> cleaned(helper, job)).thenSucceed();
    }

    private static void terrainWorker(GameTestHelper helper) {
        try {
            var level = helper.getLevel();
            PreparationGate gate = new PreparationGate();
            ResourceLocation strategy = ResourceLocation.parse("instancednotinfinite:test_" + UUID.randomUUID().toString().replace("-", ""));
            CustomTerrainStrategies.register(strategy, (plan, palette, x, y, z) -> {
                gate.awaitRelease();
                return y <= plan.terrainSurfaceY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
            });
            var definition = DefinitionParser.parse("instancednotinfinite:async_terrain", JsonParser.parseString("""
                {"formatVersion":1,"structure":"minecraft:igloo","biomes":["minecraft:plains"],
                 "environment":{"type":"custom","customStrategy":"%s"}}
                """.formatted(strategy)));
            var original = GenerationPlan.fallback(123L, definition);
            var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
            var generator = new DungeonChunkGenerator(biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS),
                level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD), original);
            var chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, level, biomes, null);
            var future = generator.fillFromNoise(Blender.empty(), level.getChunkSource().randomState(), level.structureManager(), chunk);
            helper.assertFalse(future.isDone(), "Terrain fill blocked until completion");
            helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(gate.started.getCount() == 0, "Waiting for terrain worker"))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertFalse(future.isDone(), "Terrain completed while blocked");
                    generator.updatePlan(new GenerationPlan(original.seed(), definition, original.structureBounds(), original.guaranteedBounds(),
                        original.envelopeBounds(), original.structureOrigin(), 90, original.entryPosition(), original.entryYaw()));
                    gate.release.countDown();
                })
                .thenWaitUntil(() -> helper.assertTrue(future.isDone(), "Waiting for terrain future"))
                .thenExecute(() -> {
                    helper.assertTrue(future.join() == chunk, "Terrain future returned a different chunk");
                    int y = original.terrainSurfaceY();
                    helper.assertTrue(chunk.getBlockState(new BlockPos(0, y, 0)).is(Blocks.STONE), "Worker did not retain its captured terrain plan");
                    helper.assertTrue(chunk.getBlockState(new BlockPos(0, y + 1, 0)).isAir(), "Worker filled above its captured surface");
                    helper.assertValueEqual(chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 0, 0), y, "Worker left a stale surface heightmap");
                }).thenSucceed();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
