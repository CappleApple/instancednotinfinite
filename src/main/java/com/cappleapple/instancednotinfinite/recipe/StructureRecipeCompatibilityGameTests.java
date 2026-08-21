package com.cappleapple.instancednotinfinite.recipe;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(InstancedNotInfinite.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("deprecation")
public final class StructureRecipeCompatibilityGameTests {
    private StructureRecipeCompatibilityGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void customPlacementRarityDoesNotResolveItsRegistrationType(GameTestHelper helper) {
        StructurePlacement placement = new StructurePlacement(
            new Vec3i(0, 0, 0), StructurePlacement.FrequencyReductionMethod.DEFAULT,
            0.4F, 0, Optional.empty()) {
            @Override
            protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
                return false;
            }

            @Override
            public StructurePlacementType<?> type() {
                throw new ClassCastException("registration wrapper must not be resolved for rarity analysis");
            }
        };

        double rarity = StructureRecipeAnalyzer.placementRarity(placement, 0.6D, 1, 1, true);
        helper.assertTrue(Math.abs(rarity - 0.72D) < 0.000_001D,
            "Custom placement rarity was not calculated from raw structure-set metadata");
        helper.succeed();
    }
}
