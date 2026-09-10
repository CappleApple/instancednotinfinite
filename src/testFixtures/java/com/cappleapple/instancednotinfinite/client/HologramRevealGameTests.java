package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.manifestation.AnimationMode;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationScoreMath;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationState;
import com.cappleapple.instancednotinfinite.manifestation.PreparationParticleStyle;
import com.cappleapple.instancednotinfinite.network.ManifestationBlocksPayload;
import com.cappleapple.instancednotinfinite.network.ManifestationProgressPayload;
import com.cappleapple.instancednotinfinite.network.ManifestationStartPayload;
import com.cappleapple.instancednotinfinite.snapshot.VisualLayer;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** Exercises the client data model against real packets/registries without requiring a GPU. */
@GameTestHolder(InstancedNotInfinite.MOD_ID)
public final class HologramRevealGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(
            new TestFunction("hologram_reveal", "instancednotinfinite.hologram_streamed_scores",
                "instancednotinfinite_integration:empty", 100, 0L, true, HologramRevealGameTests::streamedScores),
            new TestFunction("hologram_reveal", "instancednotinfinite.hologram_completion_clock",
                "instancednotinfinite_integration:empty", 100, 0L, true, HologramRevealGameTests::completionClock));
    }

    private static void streamedScores(GameTestHelper helper) {
        ClientManifestation value = new ClientManifestation(start(AnimationMode.GROUND_UP));
        int stone = Block.BLOCK_STATE_REGISTRY.getId(Blocks.DEEPSLATE.defaultBlockState());
        var high = new ManifestationBlocksPayload.Entry(2, 24, 2, stone, VisualLayer.STRUCTURE);
        var low = new ManifestationBlocksPayload.Entry(2, 4, 2, stone, VisualLayer.STRUCTURE);
        var middle = new ManifestationBlocksPayload.Entry(2, 7, 2, stone, VisualLayer.STRUCTURE);
        value.add(new ManifestationBlocksPayload(value.id(), List.of(high)));
        value.add(new ManifestationBlocksPayload(value.id(), List.of(low, middle)));
        for (ClientManifestation.ClientVisualBlock block : value.snapshotBlocks()) {
            BlockPos pos = block.position();
            double expected = ManifestationScoreMath.score(pos.getX(), pos.getY(), pos.getZ(),
                0, 0, 0, 31, 31, 31, AnimationMode.GROUND_UP, 42L);
            helper.assertValueEqual(block.score(), expected, "Packet order flattened a later batch's raw scores");
        }
        ClientManifestation reverse = new ClientManifestation(start(AnimationMode.GROUND_UP));
        reverse.add(new ManifestationBlocksPayload(reverse.id(), List.of(low, middle, high)));
        for (ClientManifestation.ClientVisualBlock block : value.snapshotBlocks()) {
            helper.assertTrue(reverse.snapshotBlocks().contains(block), "Batch boundaries changed reveal order");
        }
        int revision = value.visualRevision();
        value.add(new ManifestationBlocksPayload(value.id(), List.of(high, low, middle)));
        helper.assertValueEqual(value.visualRevision(), revision, "Repeated snapshot unnecessarily invalidated its mesh");
        helper.succeed();
    }

    private static void completionClock(GameTestHelper helper) {
        ClientManifestation value = new ClientManifestation(start(AnimationMode.CHAOTIC));
        value.update(progress(value.id(), 0.5F, 1.0F, ManifestationState.MANIFESTING));
        for (int tick = 0; tick < 100; tick++) value.tick();
        helper.assertTrue(value.progress() <= 0.5F, "Visual clock bypassed unfinished generation");
        value.update(progress(value.id(), 1.0F, 1.0F, ManifestationState.COLLAPSING));
        value.tick();
        helper.assertValueEqual(value.progress(), 1.0F, "Final model was still hidden during collapse");
        helper.assertValueEqual(value.animationProgress(), value.progress(), "Completed tooltip and reveal disagree");
        helper.assertTrue(value.animatedReveal(), "Animated mode lost its reveal");
        helper.assertFalse(new ClientManifestation(start(AnimationMode.NONE)).animatedReveal(), "NONE acquired an animation");
        helper.succeed();
    }

    private static ManifestationProgressPayload progress(UUID id, float generation, float animation, ManifestationState state) {
        return new ManifestationProgressPayload(id, state, generation, animation, 0L,
            0xFFFFFF, 0xFFFFFF, 0, 0, false);
    }

    private static ManifestationStartPayload start(AnimationMode mode) {
        UUID id = UUID.randomUUID();
        return new ManifestationStartPayload(id, Level.OVERWORLD.location(), BlockPos.ZERO, 0, id,
            ResourceLocation.parse("minecraft:ancient_city"), 42L, mode, ManifestationState.MANIFESTING,
            0.0F, 0.0F, 0L, 32, 32, 32, 0, 0, 0, 31, 31, 31,
            3.0F, 3.0F, 3.0F, 0.0F, 1.0F, 20, 20, 20,
            2.0F, 3.0F, 1.0F, 1.0F, 2.0F, 0.35F, 0xFFFFFF, 0xFFFFFF,
            PreparationParticleStyle.NONE, 0xFFFFFF, 0, 0.7F, 1.75F);
    }
}
