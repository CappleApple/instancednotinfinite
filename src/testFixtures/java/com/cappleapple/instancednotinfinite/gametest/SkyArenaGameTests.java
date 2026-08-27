package com.cappleapple.instancednotinfinite.gametest;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.content.ManifestationPortalBlockEntity;
import com.cappleapple.instancednotinfinite.content.ModContent;
import com.cappleapple.instancednotinfinite.instance.DestinationPortalPlacement;
import com.cappleapple.instancednotinfinite.instance.DungeonInstance;
import com.cappleapple.instancednotinfinite.instance.DungeonInstanceManager;
import com.cappleapple.instancednotinfinite.instance.InstanceOperationException;
import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** Optional real-mod regressions for protection activated partway through structure placement. */
@GameTestHolder(InstancedNotInfinite.MOD_ID)
public final class SkyArenaGameTests {
    private SkyArenaGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        if (!ModList.get().isLoaded("skyarena")) return List.of();
        return List.of(test("ice_arena"), test("sky_arena"));
    }

    private static TestFunction test(String name) {
        return new TestFunction("sky_arena", "instancednotinfinite." + name + "_keeps_complete_template",
            "instancednotinfinite_integration:empty", 1200, 0L, true,
            helper -> createDeleteRecreate(helper, ResourceLocation.fromNamespaceAndPath("skyarena", name)));
    }

    private static void createDeleteRecreate(GameTestHelper helper, ResourceLocation id) {
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        DungeonInstance first = createAndCheck(helper, manager, id, false);
        delete(manager, first);
        AtomicReference<DungeonInstance> second = new AtomicReference<>();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(manager.get(first.id()).isEmpty(), "Waiting for first arena cleanup"))
            .thenExecute(() -> {
                // Also cover packs whose configured protection extends beyond the authored arena.
                DungeonInstance recreated = createAndCheck(helper, manager, id, true);
                second.set(recreated);
                helper.assertFalse(first.id().equals(recreated.id()), "Recreated arena reused its deleted instance");
                delete(manager, recreated);
            })
            .thenWaitUntil(() -> helper.assertTrue(manager.get(second.get().id()).isEmpty(), "Waiting for recreated arena cleanup"))
            .thenSucceed();
    }

    private static DungeonInstance createAndCheck(GameTestHelper helper, DungeonInstanceManager manager, ResourceLocation id,
        boolean protectEntireApproach) {
        List<String> previous = List.copyOf(ServerConfig.INSTANCE.structures.get());
        DungeonInstance instance = null;
        try (ProtectionRadiusOverride ignored = protectEntireApproach ? ProtectionRadiusOverride.expand(id) : null) {
            ServerConfig.INSTANCE.structures.set(List.of(id.toString()));
            manager.rebuildCatalogue();
            instance = manager.create(id);
            ServerLevel level = helper.getLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, instance.dimensionId()));
            GenerationPlan plan = instance.plan().orElseThrow();
            helper.assertTrue(plan.floatingVoid(), "Sky arena did not use floating placement");
            var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
            var start = level.getChunk(0, 0).getStartForStructure(structure);
            helper.assertTrue(start != null && start.getPieces().size() == 1, "Expected the arena's single template piece");
            var piece = (PoolElementStructurePiece)start.getPieces().getFirst();
            var settings = new StructurePlaceSettings().setRotation(piece.getRotation());
            StructureTemplate template = level.getStructureManager().getOrCreate(id);
            CompoundTag nbt = template.save(new CompoundTag());
            ListTag palette = nbt.getList("palette", Tag.TAG_COMPOUND);
            List<BlockState> states = new ArrayList<>();
            for (Tag value : palette) states.add(NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK), (CompoundTag)value));
            int checked = 0;
            int missing = 0;
            BlockPos altar = null;
            String arenaType = null;
            List<String> examples = new ArrayList<>();
            for (Tag value : nbt.getList("blocks", Tag.TAG_COMPOUND)) {
                CompoundTag block = (CompoundTag)value;
                BlockState expected = states.get(block.getInt("state"));
                ListTag coordinates = block.getList("pos", Tag.TAG_INT);
                BlockPos local = new BlockPos(coordinates.getInt(0), coordinates.getInt(1), coordinates.getInt(2));
                BlockPos world = StructureTemplate.calculateRelativePosition(settings, local).offset(piece.getPosition());
                if (block.getCompound("nbt").contains("ArenaType")) {
                    altar = world;
                    arenaType = block.getCompound("nbt").getString("ArenaType");
                }
                if (expected.isAir() || !expected.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    || isEntrancePassage(plan, world)) continue;
                checked++;
                if (!level.getBlockState(world).is(expected.getBlock())) {
                    missing++;
                    if (examples.size() < 8) examples.add(world.toShortString() + " expected " + expected + " got " + level.getBlockState(world));
                }
            }
            InstancedNotInfinite.LOGGER.info("Arena template comparison {}: rotation={}, checked={}, missing={}, examples={}",
                id, piece.getRotation(), checked, missing, examples);
            helper.assertTrue(checked > 1000, "Arena template comparison did not inspect substantial authored geometry");
            helper.assertValueEqual(missing, 0, "Arena lost authored blocks outside the entrance passage: " + examples);
            helper.assertTrue(altar != null && level.getBlockEntity(altar) != null, "Arena altar or its template data was lost");
            helper.assertValueEqual(level.getBlockEntity(altar).saveCustomOnly(level.registryAccess()).getString("ArenaType"),
                arenaType, "Arena altar lost its own protection/configuration type");
            BlockPos protectedFloor = altar.below();
            helper.assertFalse(level.getBlockState(protectedFloor).isAir(), "No floor beneath the arena altar");
            assertGameplayEditRejected(helper, level, protectedFloor);
            assertApproachAndPortal(helper, level, instance, id, protectEntireApproach);
            return instance;
        } catch (InstanceOperationException exception) {
            throw new IllegalStateException("Could not create real arena " + id, exception);
        } catch (RuntimeException exception) {
            if (instance != null) delete(manager, instance);
            throw exception;
        } finally {
            ServerConfig.INSTANCE.structures.set(previous);
            manager.rebuildCatalogue();
        }
    }

    private static void assertApproachAndPortal(GameTestHelper helper, ServerLevel level, DungeonInstance instance,
        ResourceLocation id, boolean protectedApproach) {
        GenerationPlan plan = instance.plan().orElseThrow();
        BlockPos entry = plan.entryPosition();
        int radius = Math.max(1, ServerConfig.INSTANCE.approachPlatformRadius.get());
        int platformClearance = Math.max(2, ServerConfig.INSTANCE.approachPlatformClearanceHeight.get());
        var platformBlock = level.registryAccess().registryOrThrow(Registries.BLOCK)
            .get(ResourceLocation.parse(ServerConfig.INSTANCE.approachPlatformBlock.get()));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos feet = entry.offset(dx, 0, dz);
                helper.assertTrue(level.getBlockState(feet.below()).is(platformBlock), "Configured platform block is missing at " + feet);
                assertWalkable(helper, level, feet, platformClearance, protectedApproach);
            }
        }

        Direction inward = Direction.fromYRot(plan.entryYaw());
        Direction side = inward.getClockWise();
        int width = Math.max(3, ServerConfig.INSTANCE.approachPathWidth.get());
        int left = (width - 1) / 2;
        int right = width / 2;
        int pathClearance = Math.max(2, ServerConfig.INSTANCE.approachPathClearanceHeight.get());
        // Cross the exterior edge and continue into the authored structure to check the carved entrance too.
        for (int step = 0; step <= ServerConfig.INSTANCE.approachDistance.get() + 2; step++) {
            for (int offset = -left; offset <= right; offset++) {
                assertWalkable(helper, level, entry.relative(inward, step).relative(side, offset), pathClearance, protectedApproach);
            }
        }

        BlockPos portalPos = DestinationPortalPlacement.position(plan, ServerConfig.INSTANCE.destinationPortalBehindEntryBlocks.get());
        helper.assertTrue(level.getBlockState(portalPos).is(ModContent.MANIFESTATION_PORTAL.get()), "Return portal was rejected by arena protection");
        helper.assertTrue(level.getBlockEntity(portalPos) instanceof ManifestationPortalBlockEntity, "Return portal block entity is missing");
        var portal = (ManifestationPortalBlockEntity)level.getBlockEntity(portalPos);
        helper.assertValueEqual(portal.endpoint(), ManifestationPortalBlockEntity.Endpoint.RETURN, "Arena portal is not a return endpoint");
        helper.assertValueEqual(portal.instanceId().orElseThrow(), instance.id().value(), "Arena return portal is bound to the wrong instance");
        helper.assertValueEqual(portal.dungeonId().orElseThrow(), id, "Arena return portal is bound to the wrong dungeon");
        assertWalkable(helper, level, portalPos, platformClearance, protectedApproach);
        if (protectedApproach) {
            helper.assertFalse(level.destroyBlock(portalPos, false), "Normal destruction bypassed protection around the generated portal");
            helper.assertTrue(level.getBlockEntity(portalPos) == portal, "Rejected portal destruction lost its bound block entity");
        }
        InstancedNotInfinite.LOGGER.info("Arena entrance protection check {}: full platform={}x{}, path width={}, portal={}, bound instance={}, entire approach protected={}",
            id, radius * 2 + 1, radius * 2 + 1, width, portalPos, instance.id(), protectedApproach);
    }

    private static void assertWalkable(GameTestHelper helper, ServerLevel level, BlockPos feet, int clearance, boolean protectedApproach) {
        helper.assertTrue(level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP), "Arena approach has no support at " + feet);
        if (protectedApproach) assertGameplayEditRejected(helper, level, feet.below());
        for (int dy = 0; dy < clearance; dy++) {
            BlockPos clear = feet.above(dy);
            helper.assertTrue(level.getBlockState(clear).getCollisionShape(level, clear).isEmpty() && level.getFluidState(clear).isEmpty(),
                "Arena approach headroom was not cleared at " + clear);
            if (protectedApproach) assertGameplayEditRejected(helper, level, clear);
        }
    }

    private static void assertGameplayEditRejected(GameTestHelper helper, ServerLevel level, BlockPos pos) {
        BlockState original = level.getBlockState(pos);
        BlockState replacement = original.is(Blocks.BEDROCK) ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.BEDROCK.defaultBlockState();
        helper.assertFalse(level.setBlock(pos, replacement, 2), "Normal gameplay edit was not blocked by active arena protection at " + pos);
        helper.assertValueEqual(level.getBlockState(pos), original, "Rejected gameplay edit changed a protected block at " + pos);
    }

    /** Test-only optional-mod access: increase protection, never disable it, and restore config without saving. */
    private record ProtectionRadiusOverride(Object config, Field radius, int previous) implements AutoCloseable {
        static ProtectionRadiusOverride expand(ResourceLocation id) {
            try {
                Object data = Class.forName("net.jrdemiurge.skyarena.config.SkyArenaConfig").getField("configData").get(null);
                Object config = ((Map<?, ?>)data.getClass().getField("arenas").get(data)).get(id.getPath());
                Field radius = config.getClass().getField("fullProtectionRadius");
                int previous = radius.getInt(config);
                radius.setInt(config, Math.max(previous, 512));
                return new ProtectionRadiusOverride(config, radius, previous);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not configure the real arena protection regression", exception);
            }
        }

        @Override
        public void close() {
            try {
                radius.setInt(config, previous);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not restore arena protection configuration", exception);
            }
        }
    }

    private static boolean isEntrancePassage(GenerationPlan plan, BlockPos pos) {
        BlockPos entry = plan.entryPosition();
        Direction inward = Direction.fromYRot(plan.entryYaw());
        Direction side = inward.getClockWise();
        int sideDistance = Math.abs((pos.getX() - entry.getX()) * side.getStepX() + (pos.getZ() - entry.getZ()) * side.getStepZ());
        int clearance = Math.max(ServerConfig.INSTANCE.approachPathClearanceHeight.get(), ServerConfig.INSTANCE.approachPlatformClearanceHeight.get());
        return sideDistance <= Math.max(1, ServerConfig.INSTANCE.approachPathWidth.get() / 2)
            && pos.getY() >= entry.getY() - 1 && pos.getY() < entry.getY() + clearance;
    }

    private static void delete(DungeonInstanceManager manager, DungeonInstance instance) {
        try {
            manager.delete(instance.id());
        } catch (InstanceOperationException exception) {
            throw new IllegalStateException("Could not clean up arena instance", exception);
        }
    }
}
