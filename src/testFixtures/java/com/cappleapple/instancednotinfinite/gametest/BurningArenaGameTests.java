package com.cappleapple.instancednotinfinite.gametest;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import com.cappleapple.instancednotinfinite.instance.DungeonInstance;
import com.cappleapple.instancednotinfinite.instance.DungeonInstanceManager;
import com.cappleapple.instancednotinfinite.instance.InstanceOperationException;
import com.cappleapple.instancednotinfinite.structure.StructureFoundationAnalyzer;
import com.cappleapple.instancednotinfinite.terrain.FoundationSeatingReference;
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
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** Runs against the pack's actual Integrated Cataclysm replacement, not a guessed box. */
@GameTestHolder(InstancedNotInfinite.MOD_ID)
public final class BurningArenaGameTests {
    private static final ResourceLocation ARENA = ResourceLocation.parse("cataclysm:burning_arena");

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        if (!ModList.get().isLoaded("cataclysm") || !ModList.get().isLoaded("integrated_cataclysm")) return List.of();
        return List.of(new TestFunction("burning_arena", "instancednotinfinite.burning_arena_uses_authored_foundation",
            "instancednotinfinite_integration:empty", 1800, 0L, true, BurningArenaGameTests::createDeleteRecreate));
    }

    private static void createDeleteRecreate(GameTestHelper helper) {
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        DungeonInstance first = createAndCheck(helper, manager);
        delete(manager, first);
        AtomicReference<DungeonInstance> second = new AtomicReference<>();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(manager.get(first.id()).isEmpty(), "Waiting for Burning Arena cleanup"))
            .thenExecute(() -> {
                second.set(createAndCheck(helper, manager));
                delete(manager, second.get());
            })
            .thenWaitUntil(() -> helper.assertTrue(manager.get(second.get().id()).isEmpty(), "Waiting for recreated Burning Arena cleanup"))
            .thenSucceed();
    }

    private static DungeonInstance createAndCheck(GameTestHelper helper, DungeonInstanceManager manager) {
        List<String> previous = List.copyOf(ServerConfig.INSTANCE.structures.get());
        DungeonInstance instance = null;
        try {
            ServerConfig.INSTANCE.structures.set(List.of(ARENA.toString()));
            manager.rebuildCatalogue();
            instance = manager.create(ARENA);
            ServerLevel level = helper.getLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, instance.dimensionId()));
            var plan = instance.plan().orElseThrow();
            var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ARENA);
            helper.assertValueEqual(plan.definition().environment(), EnvironmentType.NETHER_LIKE, "Burning Arena lost its Nether terrain handler");
            helper.assertFalse(plan.floatingVoid(), "Grounded Burning Arena was turned into a sky structure");
            var start = level.getChunk(0, 0).getStartForStructure(structure);
            var profile = StructureFoundationAnalyzer.profile(level, start).orElseThrow();
            int expectedSurface = FoundationSeatingReference.select(profile.foundation(), profile.placementGroundY(),
                structure.terrainAdaptation() != TerrainAdjustment.NONE) - 1;
            InstancedNotInfinite.LOGGER.info("Burning Arena foundation comparison: authored={}..{}, ground={}, surface={}, expected={}, entry={}",
                profile.foundation().baseY(), profile.foundation().topY(), profile.placementGroundY(),
                plan.terrainSurfaceY(), expectedSurface, plan.entryPosition());
            helper.assertTrue(expectedSurface < 63, "Fixture no longer exercises partial burial below the flat Y=63 sample");
            helper.assertValueEqual(plan.terrainSurfaceY(), expectedSurface, "Generic flat terrain buried the arena above its authored foundation");
            helper.assertValueEqual(plan.entryPosition().getY(), expectedSurface + 1, "Entrance was not retained at the corrected terrain surface");
            // The guaranteed flat skirt extends two blocks beyond the measured box; farther out has intentional noise.
            BlockPos surface = new BlockPos(plan.structureBounds().minX() - 1, expectedSurface, plan.structureBounds().minZ() - 1);
            helper.assertFalse(level.getBlockState(surface).isAir(), "Corrected Nether terrain has no supporting surface");
            helper.assertTrue(level.getBlockState(surface.above()).isAir(), "Nether terrain still rises above the authored foundation");
            helper.assertTrue(level.getBlockState(plan.entryPosition()).getCollisionShape(level, plan.entryPosition()).isEmpty(),
                "Corrected Burning Arena entrance is obstructed");
            return instance;
        } catch (InstanceOperationException exception) {
            throw new IllegalStateException("Could not create real Burning Arena", exception);
        } catch (RuntimeException exception) {
            if (instance != null) delete(manager, instance);
            throw exception;
        } finally {
            ServerConfig.INSTANCE.structures.set(previous);
            manager.rebuildCatalogue();
        }
    }

    private static void delete(DungeonInstanceManager manager, DungeonInstance instance) {
        try {
            manager.delete(instance.id());
        } catch (InstanceOperationException exception) {
            throw new IllegalStateException("Could not clean up Burning Arena instance", exception);
        }
    }
}
