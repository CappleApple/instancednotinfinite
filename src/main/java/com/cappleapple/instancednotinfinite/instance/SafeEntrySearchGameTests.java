package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.definition.BiomeRule;
import com.cappleapple.instancednotinfinite.definition.DecorationMode;
import com.cappleapple.instancednotinfinite.definition.DungeonDefinition;
import com.cappleapple.instancednotinfinite.definition.EntryPoint;
import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import com.cappleapple.instancednotinfinite.definition.HeightContext;
import com.cappleapple.instancednotinfinite.definition.PlacementMode;
import com.cappleapple.instancednotinfinite.definition.ReentryPolicy;
import com.cappleapple.instancednotinfinite.definition.StructureKind;
import com.cappleapple.instancednotinfinite.definition.TerrainSettings;
import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(InstancedNotInfinite.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SafeEntrySearchGameTests {
    private static final String TEST_TEMPLATE = "bastion/mobs/empty";

    private SafeEntrySearchGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE)
    public static void automaticSearchStaysInsideFullStructureFootprint(GameTestHelper helper) {
        BoundingBox structure = new BoundingBox(-39, 49, -55, 38, 155, 55);
        GenerationPlan plan = GenerationPlan.fromBounds(
            4L, definition(), structure, new BlockPos(-39, 49, -55), true);
        BlockPos unrelatedCavityFloor = new BlockPos(0, 36, 0);
        BlockPos structureInterior = new BlockPos(30, 50, 0);

        BlockPos selected = SafeEntrySearch.automatic(
            plan, candidate -> candidate.equals(unrelatedCavityFloor) || candidate.equals(structureInterior))
            .orElseThrow();

        helper.assertValueEqual(selected, structureInterior,
            "Automatic entry search escaped below the structure instead of scanning its full footprint");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE)
    public static void automaticSurfaceEntryUsesExteriorApproachAtGenerationSurface(GameTestHelper helper) {
        BoundingBox structure = new BoundingBox(-39, 49, -55, 38, 155, 55);
        GenerationPlan plan = GenerationPlan.fromBounds(
            5L, definition(), structure, new BlockPos(-39, 49, -55), true, 127);
        BlockPos expectedApproach = new BlockPos(0, 128, -57);

        BlockPos selected = AutomaticEntryLocator.find(
            helper.getLevel(), plan, candidate -> candidate.equals(expectedApproach))
            .orElseThrow();

        helper.assertValueEqual(selected, expectedApproach,
            "Automatic surface entry did not use the exterior approach at the retained generation surface");
        helper.assertTrue(selected.getZ() < structure.minZ(),
            "Automatic surface entry was placed inside, above, or below the structure instead of outside it");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = TEST_TEMPLATE)
    public static void detectedFrontBuildsConfiguredPlatformAndPath(GameTestHelper helper) {
        BlockPos corner = new BlockPos(0, 128, 0);
        int surfaceY = corner.getY();
        BoundingBox structure = new BoundingBox(
            corner.getX(), surfaceY, corner.getZ(),
            corner.getX() + 8, surfaceY + 8, corner.getZ() + 8);
        GenerationPlan plan = GenerationPlan.fromBounds(
            6L, definition(), structure, corner, true, surfaceY);
        BlockPos doorway = new BlockPos(
            structure.minX() + structure.getXSpan() / 2, surfaceY + 1, structure.maxZ());
        helper.getLevel().setBlockAndUpdate(doorway.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(doorway, Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.getLevel().setBlockAndUpdate(doorway.above(), Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

        BlockPos expectedExterior = new BlockPos(doorway.getX(), doorway.getY(), structure.maxZ() + 1);
        BlockPos expectedSpawn = expectedExterior.relative(Direction.SOUTH, 6);
        for (int offsetY = 0; offsetY < 3; offsetY++) {
            helper.getLevel().setBlockAndUpdate(expectedExterior.above(offsetY), Blocks.STONE.defaultBlockState());
        }
        for (int offsetY = 0; offsetY < 4; offsetY++) {
            helper.getLevel().setBlockAndUpdate(expectedSpawn.above(offsetY), Blocks.STONE.defaultBlockState());
        }

        AutomaticEntryLocator.Approach approach = AutomaticEntryLocator.locate(helper.getLevel(), plan).orElseThrow();
        AutomaticApproachBuilder.BuiltApproach built;
        try {
            built = AutomaticApproachBuilder.build(
                helper.getLevel(), plan, approach,
                new AutomaticApproachBuilder.Settings(
                    6, 2, 3, "minecraft:crying_obsidian", "minecraft:smooth_stone", 3));
        } catch (InstanceOperationException exception) {
            throw new AssertionError(exception);
        }

        helper.assertValueEqual(approach.outward(), Direction.SOUTH, "Door orientation and nearest edge did not identify the front");
        helper.assertValueEqual(built.spawn(), expectedSpawn, "Player platform was not the configured distance outside the front");
        helper.assertTrue(helper.getLevel().getBlockState(expectedSpawn.below()).is(Blocks.CRYING_OBSIDIAN),
            "Configured platform block was not placed below the spawn");
        helper.assertTrue(helper.getLevel().getBlockState(expectedExterior.below()).is(Blocks.SMOOTH_STONE),
            "Configured path block did not reach the structure edge");
        helper.assertTrue(helper.getLevel().getBlockState(expectedSpawn).isAir(), "Spawn feet space was not cleared");
        helper.assertTrue(helper.getLevel().getBlockState(expectedSpawn.above()).isAir(), "Spawn head space was not cleared");
        helper.assertTrue(helper.getLevel().getBlockState(expectedExterior.above(2)).isAir(),
            "Bridge/path did not clear three blocks above its floor");
        helper.assertTrue(helper.getLevel().getBlockState(expectedSpawn.above(3)).isAir(),
            "Arrival platform did not clear four blocks above its floor");
        helper.assertValueEqual(built.yaw(), 180.0F, "Player does not face back toward the detected entrance");
        BlockPos expectedPortal = expectedSpawn.relative(Direction.SOUTH, 3);
        helper.assertTrue(helper.getLevel().getBlockState(expectedPortal.below()).is(Blocks.CRYING_OBSIDIAN),
            "Arrival platform did not extend safely to the configured return-portal position");
        helper.assertTrue(helper.getLevel().getBlockState(expectedPortal).isAir(),
            "Configured return-portal position was not cleared");
        helper.succeed();
    }

    private static DungeonDefinition definition() {
        return new DungeonDefinition(
            "example:blacksmith", 1, "example:blacksmith", StructureKind.WORLDGEN, 1,
            List.of(new BiomeRule("minecraft:soul_sand_valley", 1)), new HeightContext(32, 96),
            EnvironmentType.NETHER_LIKE, null, new TerrainSettings(48, 32, 256),
            com.cappleapple.instancednotinfinite.definition.PortalSettings.DEFAULT,
            new EntryPoint(0, 1, 0, 0.0F, 0.0F), PlacementMode.NATURAL,
            DecorationMode.NONE, false, ReentryPolicy.WHILE_ACTIVE);
    }
}
