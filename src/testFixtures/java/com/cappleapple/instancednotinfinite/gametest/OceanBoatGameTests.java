package com.cappleapple.instancednotinfinite.gametest;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.definition.AutomaticDungeonResolver;
import com.cappleapple.instancednotinfinite.definition.DungeonOverride;
import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import com.cappleapple.instancednotinfinite.definition.ResolutionException;
import com.cappleapple.instancednotinfinite.instance.DungeonInstance;
import com.cappleapple.instancednotinfinite.instance.DungeonInstanceManager;
import com.cappleapple.instancednotinfinite.instance.InstanceOperationException;
import com.cappleapple.instancednotinfinite.terrain.DungeonChunkGenerator;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** Optional real-mod regression; runs only when Awesome Dungeon Ocean is in the dev server's mods folder. */
@GameTestHolder(InstancedNotInfinite.MOD_ID)
public final class OceanBoatGameTests {
    private static final ResourceLocation FRIGATE = ResourceLocation.parse("awesomedungeonocean:frigate_large");

    private OceanBoatGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        if (!ModList.get().isLoaded("awesomedungeonocean")) return List.of();
        return List.of(new TestFunction(
            "ocean_boat", "instancednotinfinite.frigate_keeps_authored_waterline",
            "instancednotinfinite_integration:empty", 1200, 0L, true, OceanBoatGameTests::frigateKeepsAuthoredWaterline),
            new TestFunction("ocean_boat", "instancednotinfinite.ocean_ruin_keeps_sampled_seabed",
                "instancednotinfinite_integration:empty", 1200, 0L, true, OceanBoatGameTests::oceanRuinKeepsSampledSeabed));
    }

    private static void oceanRuinKeepsSampledSeabed(GameTestHelper helper) {
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        List<String> previous = List.copyOf(ServerConfig.INSTANCE.structures.get());
        ResourceLocation id = ResourceLocation.parse("awesomedungeonocean:ocean_cage");
        DungeonInstance instance;
        try {
            ServerConfig.INSTANCE.structures.set(List.of(id.toString()));
            manager.rebuildCatalogue();
            instance = manager.create(id);
            var plan = instance.plan().orElseThrow();
            helper.assertValueEqual(plan.definition().environment(), EnvironmentType.UNDERWATER, "Ocean ruin became a surface ship");
            helper.assertValueEqual(plan.terrainSurfaceY(), 63, "Ocean ruin raised its waterline above the sampled sea level");
            helper.assertValueEqual(plan.oceanFloorY(), Integer.valueOf(39), "Ocean ruin lost its sampled seabed");
            delete(helper, manager, instance);
        } catch (InstanceOperationException exception) {
            throw new IllegalStateException("Could not create the real seabed structure", exception);
        } finally {
            ServerConfig.INSTANCE.structures.set(previous);
            manager.rebuildCatalogue();
        }
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(manager.get(instance.id()).isEmpty(),
            "Waiting for ocean-ruin cleanup")).thenSucceed();
    }

    private static void frigateKeepsAuthoredWaterline(GameTestHelper helper) {
        try {
            var access = helper.getLevel().registryAccess();
            var ruin = AutomaticDungeonResolver.resolve(
                access, ResourceLocation.parse("awesomedungeonocean:ocean_cage"), List.of("test"), null, 24, 16, 256);
            helper.assertValueEqual(ruin.definition().environment(), EnvironmentType.UNDERWATER,
                "An actual ocean-floor ruin was reclassified as a surface boat");
            var explicitUnderwater = AutomaticDungeonResolver.resolve(
                access, FRIGATE, List.of("test"),
                new DungeonOverride(EnvironmentType.UNDERWATER, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null), 24, 16, 256);
            helper.assertValueEqual(explicitUnderwater.definition().environment(), EnvironmentType.UNDERWATER,
                "Boat inference overrode an explicit environment setting");
        } catch (ResolutionException exception) {
            helper.fail("Could not resolve real ocean structure metadata: " + exception.getMessage());
        }

        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        DungeonInstance first = createAndCheckFrigate(helper, manager);
        delete(helper, manager, first);
        AtomicReference<DungeonInstance> recreated = new AtomicReference<>();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(manager.get(first.id()).isEmpty(), "Waiting for first frigate cleanup"))
            .thenExecute(() -> {
                DungeonInstance second = createAndCheckFrigate(helper, manager);
                recreated.set(second);
                helper.assertFalse(first.id().equals(second.id()), "Recreated frigate reused its deleted instance");
                delete(helper, manager, second);
            })
            .thenWaitUntil(() -> helper.assertTrue(
                manager.get(recreated.get().id()).isEmpty(), "Waiting for recreated frigate cleanup"))
            .thenSucceed();
    }

    private static DungeonInstance createAndCheckFrigate(GameTestHelper helper, DungeonInstanceManager manager) {
        List<String> previousStructures = List.copyOf(ServerConfig.INSTANCE.structures.get());
        DungeonInstance instance = null;
        try {
            ServerConfig.INSTANCE.structures.set(List.of(FRIGATE.toString()));
            manager.rebuildCatalogue();
            instance = manager.create(FRIGATE);
            var plan = instance.plan().orElseThrow();
            ServerLevel level = helper.getLevel().getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, instance.dimensionId()));
            helper.assertTrue(level != null, "Frigate runtime level was not registered");
            DungeonChunkGenerator generator = (DungeonChunkGenerator)level.getChunkSource().getGenerator();
            int seaLevel = generator.getSeaLevel();
            helper.assertValueEqual(plan.definition().environment(), EnvironmentType.OCEAN_SURFACE,
                "Frigate was classified as underwater");
            helper.assertValueEqual(plan.terrainSurfaceY(), seaLevel,
                "Frigate inherited the temporary fallback waterline instead of its authored sea level");
            helper.assertTrue(plan.structureBounds().minY() < seaLevel && plan.structureBounds().maxY() > seaLevel,
                "Frigate hull did not straddle its authored waterline");
            BlockPos water = new BlockPos(plan.guaranteedBounds().minX() + 2, seaLevel,
                plan.guaranteedBounds().minZ() + 2);
            helper.assertTrue(level.getBlockState(water).is(Blocks.WATER), "Ocean water did not reach the ship's waterline");
            helper.assertTrue(level.getBlockState(water.above()).isAir(), "Ocean water rose above the ship's waterline");
            helper.assertTrue(plan.entryPosition().getY() > seaLevel, "Frigate arrival point was underwater");
            helper.assertTrue(level.getFluidState(plan.entryPosition()).isEmpty(), "Frigate arrival point contained water");
            InstancedNotInfinite.LOGGER.info(
                "Verified real frigate {}: bounds Y={}..{}, waterline Y={}, entry Y={}",
                instance.id(), plan.structureBounds().minY(), plan.structureBounds().maxY(),
                plan.terrainSurfaceY(), plan.entryPosition().getY());
            return instance;
        } catch (InstanceOperationException exception) {
            throw new IllegalStateException("Could not create the real frigate", exception);
        } catch (RuntimeException exception) {
            if (instance != null) delete(helper, manager, instance);
            throw exception;
        } finally {
            ServerConfig.INSTANCE.structures.set(previousStructures);
            manager.rebuildCatalogue();
        }
    }

    private static void delete(GameTestHelper helper, DungeonInstanceManager manager, DungeonInstance instance) {
        try {
            manager.delete(instance.id());
        } catch (InstanceOperationException exception) {
            helper.fail("Could not clean up frigate instance: " + exception.getMessage());
        }
    }
}
