package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(InstancedNotInfinite.MOD_ID)
public final class NaturalSpawningGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(
            test("bounds", NaturalSpawningGameTests::bounds),
            test("restored", NaturalSpawningGameTests::restored),
            test("disabled", NaturalSpawningGameTests::disabled));
    }

    private static TestFunction test(String name, java.util.function.Consumer<GameTestHelper> run) {
        return new TestFunction("natural_spawning", "instancednotinfinite.natural_spawning_" + name,
            "instancednotinfinite_integration:empty", 1200, 0L, true, run);
    }

    private static void bounds(GameTestHelper helper) {
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        List<DungeonInstance> instances = new ArrayList<>();
        try {
            for (String name : List.of("surface_igloo", "placement_ground")) {
                DungeonInstance instance = create(manager, name, true);
                instances.add(instance);
                ServerLevel level = level(helper, instance);
                BoundingBox box = instance.plan().orElseThrow().structureBounds();
                BlockPos center = box.getCenter();
                List<BlockPos> edges = List.of(
                    new BlockPos(box.minX(), center.getY(), center.getZ()),
                    new BlockPos(box.maxX(), center.getY(), center.getZ()),
                    new BlockPos(center.getX(), box.minY(), center.getZ()),
                    new BlockPos(center.getX(), box.maxY(), center.getZ()),
                    new BlockPos(center.getX(), center.getY(), box.minZ()),
                    new BlockPos(center.getX(), center.getY(), box.maxZ()));
                List<BlockPos> outside = List.of(edges.get(0).west(), edges.get(1).east(),
                    edges.get(2).below(), edges.get(3).above(), edges.get(4).north(), edges.get(5).south());
                for (MobSpawnType reason : List.of(MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION)) {
                    for (BlockPos pos : edges) assertResult(helper, level, pos, reason, MobSpawnEvent.PositionCheck.Result.DEFAULT);
                    for (BlockPos pos : outside) assertResult(helper, level, pos, reason, MobSpawnEvent.PositionCheck.Result.FAIL);
                    assertResult(helper, level, instance.plan().orElseThrow().entryPosition(), reason,
                        box.isInside(instance.plan().orElseThrow().entryPosition())
                            ? MobSpawnEvent.PositionCheck.Result.DEFAULT : MobSpawnEvent.PositionCheck.Result.FAIL);
                }
                helper.assertValueEqual(check(level, box.minX() - 0.01, center.getY(), center.getZ() + 0.5,
                    MobSpawnType.NATURAL).getResult(), MobSpawnEvent.PositionCheck.Result.FAIL,
                    "Fractional negative-side spawn escaped the bounds");
                helper.assertValueEqual(check(level, box.maxX() + 0.99, center.getY(), center.getZ() + 0.5,
                    MobSpawnType.NATURAL).getResult(), MobSpawnEvent.PositionCheck.Result.DEFAULT,
                    "Last block inside the volume was rejected");
                int chunksBefore = level.getChunkSource().getLoadedChunksCount();
                assertResult(helper, level, new BlockPos(1_000_000, center.getY(), 1_000_000), MobSpawnType.NATURAL,
                    MobSpawnEvent.PositionCheck.Result.FAIL);
                helper.assertValueEqual(level.getChunkSource().getLoadedChunksCount(), chunksBefore,
                    "Spawn boundary check loaded distant terrain");
                verifyVanillaHook(helper, level, center, true);
                verifyVanillaHook(helper, level, outside.get(0), false);
                for (MobSpawnType reason : MobSpawnType.values()) {
                    if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
                        assertResult(helper, level, outside.get(0), reason, MobSpawnEvent.PositionCheck.Result.DEFAULT);
                    }
                }
            }
            assertResult(helper, helper.getLevel(), helper.absolutePos(BlockPos.ZERO), MobSpawnType.NATURAL,
                MobSpawnEvent.PositionCheck.Result.DEFAULT);
        } finally {
            for (DungeonInstance instance : instances) delete(manager, instance);
        }
        cleanup(helper, manager, instances);
    }

    private static void restored(GameTestHelper helper) {
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        DungeonInstance instance = create(manager, "surface_igloo", true);
        var data = DungeonInstanceSavedData.get(helper.getLevel().getServer());
        try {
            ServerLevel level = level(helper, instance);
            DungeonInstance restored = DungeonInstance.load(instance.save()).orElseThrow();
            data.put(restored);
            BoundingBox box = restored.plan().orElseThrow().structureBounds();
            assertResult(helper, level, box.getCenter(), MobSpawnType.NATURAL, MobSpawnEvent.PositionCheck.Result.DEFAULT);
            assertResult(helper, level, new BlockPos(box.maxX() + 1, box.getCenter().getY(), box.getCenter().getZ()),
                MobSpawnType.NATURAL, MobSpawnEvent.PositionCheck.Result.FAIL);
            var noPlan = instance.save();
            noPlan.remove("Plan");
            data.put(DungeonInstance.load(noPlan).orElseThrow());
            assertResult(helper, level, box.getCenter(), MobSpawnType.NATURAL, MobSpawnEvent.PositionCheck.Result.FAIL);
        } finally {
            data.put(instance);
            delete(manager, instance);
        }
        cleanup(helper, manager, List.of(instance));
    }

    private static void disabled(GameTestHelper helper) {
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        DungeonInstance instance = create(manager, "surface_igloo", false);
        try {
            ServerLevel level = level(helper, instance);
            BlockPos center = instance.plan().orElseThrow().structureBounds().getCenter();
            helper.assertFalse(instance.definition().allowNaturalMobSpawning(), "Global spawning switch was not captured");
            assertResult(helper, level, center, MobSpawnType.NATURAL, MobSpawnEvent.PositionCheck.Result.FAIL);
            assertResult(helper, level, center, MobSpawnType.CHUNK_GENERATION, MobSpawnEvent.PositionCheck.Result.FAIL);
            assertResult(helper, level, center, MobSpawnType.SPAWNER, MobSpawnEvent.PositionCheck.Result.DEFAULT);
        } finally {
            delete(manager, instance);
        }
        cleanup(helper, manager, List.of(instance));
    }

    private static MobSpawnEvent.PositionCheck check(ServerLevel level, double x, double y, double z, MobSpawnType reason) {
        Zombie mob = new Zombie(level);
        mob.setPos(x, y, z);
        return NeoForge.EVENT_BUS.post(new MobSpawnEvent.PositionCheck(mob, level, reason, null));
    }

    private static void assertResult(GameTestHelper helper, ServerLevel level, BlockPos pos, MobSpawnType reason,
        MobSpawnEvent.PositionCheck.Result expected) {
        helper.assertValueEqual(check(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, reason).getResult(),
            expected, reason + " check at " + pos);
    }

    private static void verifyVanillaHook(GameTestHelper helper, ServerLevel level, BlockPos pos, boolean expected) {
        for (BlockPos clear : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 2, 1))) {
            level.setBlock(clear, Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        // Slime-chunk/light placement rules run earlier; this Mob-level probe has no daylight preference.
        Slime mob = new Slime(EntityType.SLIME, level);
        mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        helper.assertTrue(mob.checkSpawnRules(level, MobSpawnType.NATURAL), "Probe failed its own spawn rules");
        helper.assertTrue(mob.checkSpawnObstruction(level), "Probe position is initially obstructed");
        helper.assertValueEqual(EventHooks.checkSpawnPosition(mob, level, MobSpawnType.NATURAL), expected,
            "NaturalSpawner's position hook ignored the structure boundary");
        if (expected) {
            // Solid-block collision is checked earlier by NaturalSpawner. This hook checks liquids.
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
            helper.assertFalse(mob.checkSpawnObstruction(level), "Liquid-obstruction fixture is not obstructed");
            helper.assertFalse(EventHooks.checkSpawnPosition(mob, level, MobSpawnType.NATURAL),
                "Inside-volume allowance bypassed vanilla obstruction checks");
        }
    }

    private static DungeonInstance create(DungeonInstanceManager manager, String name, boolean allow) {
        var previous = List.copyOf(ServerConfig.INSTANCE.structures.get());
        boolean previousAllow = ServerConfig.INSTANCE.allowNaturalMobSpawning.get();
        ResourceLocation id = ResourceLocation.parse("instancednotinfinite:" + name);
        try {
            ServerConfig.INSTANCE.structures.set(List.of("instancednotinfinite:placement_ground"));
            ServerConfig.INSTANCE.allowNaturalMobSpawning.set(allow);
            manager.rebuildCatalogue();
            return manager.create(id);
        } catch (InstanceOperationException exception) {
            throw new IllegalStateException(exception);
        } finally {
            ServerConfig.INSTANCE.structures.set(previous);
            ServerConfig.INSTANCE.allowNaturalMobSpawning.set(previousAllow);
            manager.rebuildCatalogue();
        }
    }

    private static ServerLevel level(GameTestHelper helper, DungeonInstance instance) {
        return helper.getLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, instance.dimensionId()));
    }

    private static void delete(DungeonInstanceManager manager, DungeonInstance instance) {
        try {
            manager.delete(instance.id());
        } catch (InstanceOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void cleanup(GameTestHelper helper, DungeonInstanceManager manager, List<DungeonInstance> instances) {
        helper.startSequence().thenWaitUntil(() -> {
            for (DungeonInstance instance : instances) {
                helper.assertTrue(manager.get(instance.id()).isEmpty(), "Waiting for spawn-test cleanup");
            }
        }).thenSucceed();
    }
}
