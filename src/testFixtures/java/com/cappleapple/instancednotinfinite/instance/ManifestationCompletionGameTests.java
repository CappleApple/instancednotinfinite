package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.content.ManifestationPortalBlockEntity;
import com.cappleapple.instancednotinfinite.manifestation.AnimationMode;
import com.cappleapple.instancednotinfinite.manifestation.DungeonManifestation;
import com.cappleapple.instancednotinfinite.manifestation.DungeonManifestationManager;
import com.cappleapple.instancednotinfinite.manifestation.DungeonTarget;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationOptions;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationState;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(InstancedNotInfinite.MOD_ID)
public final class ManifestationCompletionGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(test(false), test(true));
    }

    private static TestFunction test(boolean forced) {
        String name = "manifestation_completion_" + (forced ? "forced" : "timed");
        return new TestFunction(name, "instancednotinfinite." + name,
            "instancednotinfinite_integration:empty", 40_000, 0L, true, helper -> opens(helper, forced));
    }

    private static void opens(GameTestHelper helper, boolean forced) {
        var level = helper.getLevel();
        var instances = DungeonInstanceManager.get(level.getServer());
        var manifestations = DungeonManifestationManager.get(level.getServer());
        var previousStructures = List.copyOf(ServerConfig.INSTANCE.structures.get());
        int previousMin = ServerConfig.INSTANCE.animationDurationMinimumTicks.get();
        int previousMax = ServerConfig.INSTANCE.animationDurationMaximumTicks.get();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        level.removeBlock(origin, false);
        level.removeBlock(origin.above(), false);
        DungeonManifestation value;
        DungeonGenerationJob job;
        try {
            ResourceLocation id = ResourceLocation.parse("instancednotinfinite:placement_ground");
            ServerConfig.INSTANCE.structures.set(List.of(id.toString()));
            // Long enough to test generation finishing before the ordinary animation clock.
            ServerConfig.INSTANCE.animationDurationMinimumTicks.set(2400);
            ServerConfig.INSTANCE.animationDurationMaximumTicks.set(2400);
            instances.rebuildCatalogue();
            value = manifestations.spawn(level, origin, DungeonTarget.dungeon(id),
                new ManifestationOptions(Direction.NORTH, AnimationMode.CHAOTIC), null);
            var jobs = DungeonManifestationManager.class.getDeclaredField("jobs");
            jobs.setAccessible(true);
            @SuppressWarnings("unchecked")
            var pending = (Map<UUID, DungeonGenerationJob>) jobs.get(manifestations);
            job = pending.get(value.id());
            if (forced) manifestations.finishAnimation(value.id());
        } catch (ReflectiveOperationException | InstanceOperationException exception) {
            throw new IllegalStateException(exception);
        } finally {
            ServerConfig.INSTANCE.structures.set(previousStructures);
            ServerConfig.INSTANCE.animationDurationMinimumTicks.set(previousMin);
            ServerConfig.INSTANCE.animationDurationMaximumTicks.set(previousMax);
            instances.rebuildCatalogue();
        }
        helper.assertFalse(job.complete(), "Fixture unexpectedly generated synchronously");
        helper.assertFalse(level.getBlockEntity(origin) instanceof ManifestationPortalBlockEntity,
            "Forced animation bypassed unfinished generation");
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(job.complete(), "Waiting for retained generation job"))
            .thenExecute(() -> {
                // One structure chunk plus its 3x3 terrain halo, heightmap pass and finalization = 12 units.
                // The old formula rounds 0.05 + 0.95 * 12 / 12 to 0.9999999999999999.
                helper.assertValueEqual(job.instance().plan().orElseThrow().structureBounds().getXSpan(), 11,
                    "Fixture no longer has the precision-regression geometry");
                helper.assertValueEqual(job.progress(), 1.0, "Ready job is stranded below exact completion");
                helper.assertTrue(job.instance().state() == InstanceState.ACTIVE || job.instance().state() == InstanceState.VACANT,
                    "Job completed without a ready instance");
                if (!forced && level.getGameTime() - value.stateChangedAtGameTime() < value.animationDurationTicks()) {
                    helper.assertValueEqual(value.state(), ManifestationState.MANIFESTING,
                        "Generation completion bypassed the unfinished animation");
                }
            })
            .thenWaitUntil(() -> helper.assertValueEqual(value.state(), ManifestationState.COLLAPSING,
                "Completed manifestation stalled instead of collapsing"))
            .thenExecute(() -> {
                helper.assertValueEqual(value.generationProgress(), 1.0, "Finalized generation progress is not exact");
                helper.assertValueEqual(value.animationProgress(), 1.0, "Finalized animation progress is not exact");
                helper.assertFalse(level.getBlockEntity(origin) instanceof ManifestationPortalBlockEntity,
                    "Portal appeared before collapse finished");
            })
            .thenWaitUntil(() -> helper.assertValueEqual(value.state(), ManifestationState.PORTAL_OPENING,
                "Completed manifestation did not create its portal"))
            .thenExecute(() -> helper.assertTrue(level.getBlockEntity(origin) instanceof ManifestationPortalBlockEntity portal
                && portal.manifestationId().filter(value.id()::equals).isPresent(), "Source portal has the wrong manifestation binding"))
            .thenWaitUntil(() -> helper.assertValueEqual(value.state(), ManifestationState.PORTAL_OPEN,
                "Source portal did not finish opening"))
            .thenExecute(() -> {
                try {
                    manifestations.cancel(value.id(), "Completion regression finished");
                } catch (InstanceOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            })
            .thenWaitUntil(() -> helper.assertTrue(value.state().terminal() && instances.get(value.instanceId()).isEmpty(),
                "Waiting for test manifestation cleanup"))
            .thenSucceed();
    }
}
