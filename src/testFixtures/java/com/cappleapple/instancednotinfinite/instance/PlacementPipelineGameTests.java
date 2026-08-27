package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import com.cappleapple.instancednotinfinite.player.PlayerReturnSavedData;
import com.cappleapple.instancednotinfinite.structure.DungeonGenerationLevel;
import com.cappleapple.instancednotinfinite.terrain.DungeonChunkGenerator;
import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(InstancedNotInfinite.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlacementPipelineGameTests {
    private static final String ARENA = "bastion/mobs/empty";

    @GameTest(templateNamespace = "minecraft", template = ARENA, timeoutTicks = 600)
    public static void generationWritesRemainBoundedAndPreserveChunkState(GameTestHelper helper) throws Exception {
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        DungeonInstance instance = create(manager, "sky");
        try {
            ServerLevel level = level(helper, instance);
            BlockPos center = instance.plan().orElseThrow().structureBounds().getCenter();
            BlockPos light = new BlockPos(center.getX() + 2, 208, center.getZ() + 2);
            BoundingBox bounds = new BoundingBox(light.getX(), light.getY(), light.getZ(), light.getX() + 1, light.getY() + 1, light.getZ() + 1);
            DungeonGenerationLevel generation = new DungeonGenerationLevel(level, bounds);
            helper.assertTrue(generation.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 2), "Worldgen write failed");
            helper.assertValueEqual(level.getHeight(Heightmap.Types.MOTION_BLOCKING, light.getX(), light.getZ()), 209,
                "Worldgen write did not update the live chunk heightmap");
            BlockPos chestPos = light.offset(1, 0, 1);
            helper.assertTrue(generation.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2), "Could not place a generated chest");
            ChestBlockEntity chest = (ChestBlockEntity)level.getBlockEntity(chestPos);
            helper.assertTrue(chest != null, "Generated chest did not register its block entity");
            chest.setItem(0, new ItemStack(Items.DIAMOND));
            generation.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2);
            helper.assertTrue(level.getBlockEntity(chestPos) == chest && chest.getItem(0).is(Items.DIAMOND),
                "Same-state generation write replaced the block entity or its data");
            for (BlockPos outside : List.of(light.west(), light.north(), light.below(), light.east(2), light.south(2), light.above(2))) {
                var before = level.getBlockState(outside);
                helper.assertFalse(generation.setBlock(outside, Blocks.BEDROCK.defaultBlockState(), 2),
                    "Generation write escaped its explicit bounds");
                helper.assertValueEqual(level.getBlockState(outside), before, "Rejected write changed a block outside the bounds");
            }
            helper.assertTrue(generation.removeBlock(light, false), "Could not remove a generated block");
            helper.assertValueEqual(level.getHeight(Heightmap.Types.MOTION_BLOCKING, light.getX(), light.getZ()), 201,
                "Removing a generated block left a stale heightmap");
        } finally {
            manager.delete(instance.id());
        }
        waitForCleanup(helper, manager, List.of(instance));
    }

    @GameTest(templateNamespace = "minecraft", template = ARENA, timeoutTicks = 600)
    public static void deferredGroundProjectionIsNotMistakenForSky(GameTestHelper helper) throws Exception {
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        List<DungeonInstance> instances = new ArrayList<>();
        try {
            for (String mode : List.of("ground", "deferred")) {
                DungeonInstance instance = create(manager, mode);
                instances.add(instance);
                GenerationPlan plan = instance.plan().orElseThrow();
                helper.assertFalse(plan.floatingVoid(), "Grounded structure was left suspended");
                helper.assertValueEqual(plan.definition().environment(), EnvironmentType.SURFACE,
                    "Provisional generation height was mistaken for final sky placement");
                BlockPos center = plan.structureBounds().getCenter();
                helper.assertFalse(level(helper, instance).getBlockState(new BlockPos(center.getX(), 40, center.getZ())).isAir(),
                    "Ground under a surface structure was removed");
            }
        } finally {
            for (DungeonInstance instance : instances) manager.delete(instance.id());
        }
        waitForCleanup(helper, manager, instances);
    }

    @GameTest(templateNamespace = "minecraft", template = ARENA, timeoutTicks = 600)
    public static void genericWaterPlacementDoesNotUseStructureIdsOrRoofHeight(GameTestHelper helper) throws Exception {
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        List<DungeonInstance> instances = new ArrayList<>();
        try {
            for (String mode : List.of("boat", "seabed", "open_seabed")) {
                DungeonInstance instance = create(manager, mode);
                instances.add(instance);
                GenerationPlan plan = instance.plan().orElseThrow();
                ServerLevel level = level(helper, instance);
                helper.assertValueEqual(instance.definition().environment(),
                    mode.equals("boat") ? EnvironmentType.OCEAN_SURFACE : EnvironmentType.UNDERWATER,
                    "Actual placement did not refine the catalogue environment");
                helper.assertValueEqual(plan.terrainSurfaceY(), 63, "Waterline changed with the roof height");
                helper.assertValueEqual(plan.oceanFloorY(), Integer.valueOf(39), "Flat sampled seabed was not retained");
                BlockPos water = new BlockPos(plan.guaranteedBounds().minX() + 2, 63, plan.guaranteedBounds().minZ() + 2);
                helper.assertTrue(level.getBlockState(water).is(Blocks.WATER), "Missing water at sampled sea level");
                helper.assertTrue(level.getBlockState(water.above()).isAir(), "Water rose above sampled sea level");
                if (mode.equals("open_seabed")) {
                    helper.assertValueEqual(plan.entryPosition().getY(), 64, "Submerged open ruin did not receive a dry surface arrival platform");
                }
                DungeonInstance loaded = DungeonInstance.load(instance.save()).orElseThrow();
                helper.assertValueEqual(loaded.plan().orElseThrow().oceanFloorY(), plan.oceanFloorY(), "Seabed lost on reload");
                helper.assertValueEqual(loaded.definition().environment(), plan.definition().environment(), "Refined environment lost on reload");
            }
        } finally {
            for (DungeonInstance instance : instances) manager.delete(instance.id());
        }
        waitForCleanup(helper, manager, instances);
    }

    @GameTest(templateNamespace = "minecraft", template = ARENA, timeoutTicks = 600)
    public static void floatingPlacementRemovesTerrainAndBuildsSafeEdgeAccess(GameTestHelper helper) throws Exception {
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        DungeonInstance instance = create(manager, "sky");
        try {
            GenerationPlan plan = instance.plan().orElseThrow();
            ServerLevel level = level(helper, instance);
            var bounds = plan.structureBounds();
            BlockPos center = bounds.getCenter();
            helper.assertTrue(plan.floatingVoid(), "Unencoded absolute sky placement was not recognized");
            helper.assertValueEqual(plan.definition().environment(), EnvironmentType.FLOATING_ISLAND, "Missing sky handler");
            helper.assertValueEqual(level.getBiome(center).unwrapKey().orElseThrow().location(),
                ResourceLocation.parse("minecraft:plains"), "Floating structure lost its intended biome");
            helper.assertTrue(level.getBlockState(new BlockPos(center.getX(), 40, center.getZ())).is(Blocks.STONE),
                "Same-state authored block was removed with temporary terrain");
            helper.assertTrue(level.getBlockState(new BlockPos(center.getX() + 1, 40, center.getZ())).isAir(),
                "Temporary terrain remained underneath the structure");
            helper.assertTrue(level.getBlockEntity(new BlockPos(center.getX(), 201, center.getZ())) != null,
                "Authored block entity was removed");
            BlockPos entry = plan.entryPosition();
            helper.assertValueEqual(entry.getY(), 201, "Arrival platform was not at the structure's surface height");
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos feet = entry.offset(dx, 0, dz);
                    helper.assertTrue(level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP),
                        "Floating arrival platform is smaller than 3x3");
                    helper.assertTrue(level.getBlockState(feet).getCollisionShape(level, feet).isEmpty(), "Unsafe arrival clearance");
                }
            }
            BlockPos portal = DestinationPortalPlacement.position(plan, ServerConfig.INSTANCE.destinationPortalBehindEntryBlocks.get());
            helper.assertTrue(level.getBlockEntity(portal) instanceof com.cappleapple.instancednotinfinite.content.ManifestationPortalBlockEntity,
                "Return portal was not created on the arrival platform");
            Direction inward = Direction.fromYRot(plan.entryYaw());
            BlockPos walk = entry;
            for (int step = 0; step < 64 && !bounds.isInside(walk.below()); step++) {
                helper.assertTrue(level.getBlockState(walk.below()).isFaceSturdy(level, walk.below(), Direction.UP),
                    "Pathway has a gap before the structure landing");
                walk = walk.relative(inward);
            }
            helper.assertTrue(bounds.isInside(walk.below()), "Arrival path does not reach the structure");
            BlockPos lazy = new BlockPos(plan.envelopeBounds().maxX() - 2, 40, plan.envelopeBounds().maxZ() - 2);
            helper.assertTrue(level.getBlockState(lazy).isAir(), "Later chunks regenerated removed terrain");
            DungeonInstance loaded = DungeonInstance.load(instance.save()).orElseThrow();
            GenerationPlan restored = loaded.plan().orElseThrow();
            helper.assertTrue(restored.floatingVoid(), "Floating cleanup state lost during save/load");
            var generator = new DungeonChunkGenerator(level.getBiome(center),
                level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD), restored);
            helper.assertValueEqual(generator.getBaseHeight(center.getX(), center.getZ(), Heightmap.Types.WORLD_SURFACE_WG,
                level, level.getChunkSource().randomState()), level.getMinBuildHeight(), "Reloaded generator regrew temporary terrain");
            InstancedNotInfinite.LOGGER.info("Verified generic floating instance {}: surface Y={}, entry={}, persisted void terrain",
                instance.id(), bounds.minY(), entry);
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (x != center.getX()) level.setBlock(new BlockPos(x, 200, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            helper.assertTrue(FloatingEntryLocator.locate(level, plan, List.of(bounds),
                AutomaticApproachBuilder.Settings.fromConfig().withMinimumLanding()).isEmpty(),
                "A one-block-wide ledge was accepted as a safe 3x3 landing");
        } finally {
            manager.delete(instance.id());
        }
        waitForCleanup(helper, manager, List.of(instance));
    }

    @GameTest(templateNamespace = "minecraft", template = ARENA, timeoutTicks = 600)
    public static void floatingEntranceCarvesThroughWallsWithoutRemovingTheLanding(GameTestHelper helper) throws Exception {
        DungeonInstanceManager manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        DungeonInstance instance = create(manager, "sky_walled");
        try {
            GenerationPlan plan = instance.plan().orElseThrow();
            ServerLevel level = level(helper, instance);
            var bounds = plan.structureBounds();
            BlockPos entry = plan.entryPosition();
            helper.assertTrue(plan.floatingVoid(), "Walled sky deck did not use floating placement");
            helper.assertValueEqual(entry.getY(), 201, "Entrance moved onto the wall instead of opening it at deck level");
            Direction inward = Direction.fromYRot(plan.entryYaw());
            Direction outward = inward.getOpposite();
            Direction sideways = outward.getClockWise();
            BlockPos opening = switch (outward) {
                case NORTH -> new BlockPos(entry.getX(), entry.getY(), bounds.minZ());
                case SOUTH -> new BlockPos(entry.getX(), entry.getY(), bounds.maxZ());
                case WEST -> new BlockPos(bounds.minX(), entry.getY(), entry.getZ());
                case EAST -> new BlockPos(bounds.maxX(), entry.getY(), entry.getZ());
                default -> throw new IllegalStateException("Non-horizontal entrance direction");
            };
            var settings = AutomaticApproachBuilder.Settings.fromConfig().withMinimumLanding();
            int left = (settings.pathWidth() - 1) / 2;
            int right = settings.pathWidth() / 2;
            BlockPos landing = opening.relative(inward, 2);
            for (int step = 0; step <= entry.distManhattan(landing); step++) {
                BlockPos center = entry.relative(inward, step);
                for (int offset = -left; offset <= right; offset++) {
                    BlockPos feet = center.relative(sideways, offset);
                    helper.assertTrue(level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP),
                        "Carved entrance route has an unsupported floor");
                    for (int dy = 0; dy < settings.pathClearanceHeight(); dy++) {
                        helper.assertTrue(level.getBlockState(feet.above(dy)).isAir(),
                            "Entrance did not carve the configured width and headroom through the wall");
                    }
                }
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.assertTrue(level.getBlockState(landing.offset(dx, -1, dz)).is(Blocks.STONE_BRICKS),
                        "Carving removed or replaced the authored 3x3 landing floor");
                }
            }
            for (int offset = -left; offset <= right; offset++) {
                BlockPos feet = opening.relative(sideways, offset);
                helper.assertTrue(level.getBlockState(feet.below()).is(Blocks.STONE_BRICKS),
                    "Carving replaced the supported floor beneath the opening");
                helper.assertTrue(level.getBlockState(feet.above(settings.pathClearanceHeight())).is(Blocks.STONE_BRICKS),
                    "Carving removed wall blocks above the configured headroom");
            }
            helper.assertTrue(level.getBlockState(opening.relative(sideways, -left - 1)).is(Blocks.STONE_BRICKS),
                "Carving widened the opening beyond the configured passage");
            helper.assertTrue(level.getBlockState(opening.relative(sideways, right + 1)).is(Blocks.STONE_BRICKS),
                "Carving widened the opening beyond the configured passage");
            var oversized = new AutomaticApproachBuilder.Settings(
                plan.guaranteedBounds().getXSpan() + plan.guaranteedBounds().getZSpan(),
                settings.platformRadius(), settings.pathWidth(), settings.pathClearanceHeight(),
                settings.platformClearanceHeight(), settings.platformBlock(), settings.pathBlock(),
                settings.destinationPortalBehindEntryBlocks());
            helper.assertTrue(FloatingEntryLocator.locate(level, plan, List.of(bounds), oversized).isEmpty(),
                "Allowing obstructed routes bypassed the guaranteed-terrain bounds check");
        } finally {
            manager.delete(instance.id());
        }
        waitForCleanup(helper, manager, List.of(instance));
    }

    @GameTest(templateNamespace = "minecraft", template = ARENA, timeoutTicks = 600)
    public static void voidFallReturnsToSourcePortalInItsOriginalDimension(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        DungeonInstanceManager manager = DungeonInstanceManager.get(server);
        DungeonInstance instance = create(manager, "sky");
        ServerLevel source = server.getLevel(Level.NETHER);
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "ini-void-test"), false);
        ServerPlayer player = new ServerPlayer(server, source, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        try {
            BlockPos portal = new BlockPos(2000, 100, 2000);
            int radius = ServerConfig.INSTANCE.sourcePortalExitOffsetBlocks.get() + 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    source.setBlock(portal.offset(dx, -1, dz), Blocks.OBSIDIAN.defaultBlockState(), 2);
                    for (int dy = 0; dy <= 2; dy++) source.setBlock(portal.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            player.teleportTo(source, 2000.5, 100, 2001.5, 0, 0);
            manager.enterFromPortal(player, instance.id(), portal, 0);
            var expected = PlayerReturnSavedData.get(server).get(player.getUUID()).orElseThrow();
            helper.assertValueEqual(expected.dimension(), Level.NETHER.location(), "Entrance source dimension was not captured");
            float health = player.getHealth();
            player.setPos(player.getX(), player.serverLevel().getMinBuildHeight(), player.getZ());
            helper.assertFalse(manager.returnFallenPlayer(player), "Return triggered before falling below minimum Y");
            player.fallDistance = 1000;
            player.setDeltaMovement(0, -4, 0);
            player.setPos(player.getX(), player.serverLevel().getMinBuildHeight() - 100, player.getZ());
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
            helper.assertTrue(player.serverLevel() == source, "Void fall did not return to the original non-overworld dimension");
            helper.assertTrue(player.position().distanceToSqr(new Vec3(expected.x(), expected.y(), expected.z())) < 0.001,
                "Void return ignored the saved entrance-portal exit position");
            helper.assertValueEqual(player.fallDistance, 0.0F, "Fall damage was carried out of the instance");
            helper.assertValueEqual(player.getDeltaMovement(), Vec3.ZERO, "Falling velocity survived the return");
            helper.assertValueEqual(player.getHealth(), health, "Void recovery damaged the player");
            helper.assertTrue(PlayerReturnSavedData.get(server).get(player.getUUID()).isEmpty(), "Return data was not consumed");
            helper.assertFalse(manager.returnFallenPlayer(player), "Normal dimensions were affected by the instance-only guard");
            manager.enterFromPortal(player, instance.id(), portal, 0);
            player.setPos(player.getX(), player.serverLevel().getMinBuildHeight() - 0.25, player.getZ());
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            helper.assertTrue(player.serverLevel() == source, "Crossing minimum Y during the tick did not trigger immediate return");
        } finally {
            manager.leave(player);
            server.getPlayerList().remove(player);
            manager.delete(instance.id());
        }
        waitForCleanup(helper, manager, List.of(instance));
    }

    private static DungeonInstance create(DungeonInstanceManager manager, String mode) throws InstanceOperationException {
        var previous = List.copyOf(ServerConfig.INSTANCE.structures.get());
        ResourceLocation id = ResourceLocation.parse("instancednotinfinite:placement_" + mode);
        try {
            ServerConfig.INSTANCE.structures.set(List.of(id.toString()));
            manager.rebuildCatalogue();
            return manager.create(id);
        } finally {
            ServerConfig.INSTANCE.structures.set(previous);
            manager.rebuildCatalogue();
        }
    }

    private static ServerLevel level(GameTestHelper helper, DungeonInstance instance) {
        return helper.getLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, instance.dimensionId()));
    }

    private static void waitForCleanup(GameTestHelper helper, DungeonInstanceManager manager, List<DungeonInstance> instances) {
        helper.startSequence().thenWaitUntil(() -> {
            for (DungeonInstance instance : instances) helper.assertTrue(manager.get(instance.id()).isEmpty(), "Waiting for placement-test cleanup");
        }).thenSucceed();
    }
}
