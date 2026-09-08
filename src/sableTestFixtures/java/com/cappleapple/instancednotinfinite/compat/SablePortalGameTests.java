package com.cappleapple.instancednotinfinite.compat;

import com.cappleapple.instancednotinfinite.content.ManifestationPortalBlockEntity;
import com.cappleapple.instancednotinfinite.content.ModContent;
import com.cappleapple.instancednotinfinite.instance.DungeonInstanceManager;
import com.cappleapple.instancednotinfinite.manifestation.*;
import com.cappleapple.instancednotinfinite.player.PlayerReturnManager;
import com.cappleapple.instancednotinfinite.player.PlayerReturnSavedData;
import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
@GameTestHolder("instancednotinfinite_sable")
public final class SablePortalGameTests {
    @GameTest(templateNamespace = "instancednotinfinite_integration", template = "empty", timeoutTicks = 24000)
    public static void movingPortalPlacementAndReturn(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerSubLevel subLevel = assembleDeck(helper);
        BlockPos anchor = BlockPos.containing(subLevel.logicalPose().rotationPoint().x(),
            subLevel.logicalPose().rotationPoint().y(), subLevel.logicalPose().rotationPoint().z()).above();
        subLevel.logicalPose().orientation().rotationY(Math.toRadians(67));
        subLevel.updateLastPose();
        helper.assertTrue(SableCoordinates.subLevelId(level, anchor).filter(subLevel.getUniqueId()::equals).isPresent(),
            "Portal block coordinates did not resolve to the assembled Sable plot");
        DungeonManifestationManager manager = DungeonManifestationManager.get(level.getServer());
        DungeonManifestation value = manager.spawn(level, anchor,
            DungeonTarget.dungeon(ResourceLocation.parse("instancednotinfinite:surface_igloo")),
            ManifestationOptions.defaults(SableCoordinates.localYaw(level, anchor, 30)), null);
        manager.finishAnimation(value.id());
        helper.startSequence()
            .thenWaitUntil(() -> {
                helper.assertFalse(value.state().terminal(), "Contraption manifestation failed: " + value.failureReason());
                // Hold a deterministic pose while the unpaced GameTest server polls background generation.
                subLevel.logicalPose().position().set(helper.absolutePos(new BlockPos(0, 10, 0)).getCenter().x,
                    helper.absolutePos(new BlockPos(0, 10, 0)).getCenter().y,
                    helper.absolutePos(new BlockPos(0, 10, 0)).getCenter().z);
                helper.assertTrue(value.state() == ManifestationState.PORTAL_OPEN, "Waiting for contraption portal");
            })
            .thenExecute(() -> {
                try {
                    helper.assertTrue(level.getBlockEntity(anchor) instanceof ManifestationPortalBlockEntity,
                        "Portal was not placed in the contraption plot");
                    ManifestationPortalBlockEntity portal = (ManifestationPortalBlockEntity)level.getBlockEntity(anchor);
                    subLevel.logicalPose().orientation().rotationY(Math.toRadians(67));
                    Vec3 center = SableCoordinates.toWorld(level, anchor.getCenter().add(0, 1, 0));
                    helper.assertTrue(portal.intersects(new AABB(center, center).inflate(0.2)),
                        "World-space player could not touch rotated portal");
                    helper.assertFalse(portal.intersects(new AABB(center.add(10, 0, 10), center.add(11, 2, 11))),
                        "Remote player activated portal");
                    helper.assertTrue(SableCoordinates.distanceSquared(level, anchor.getCenter(),
                        SableCoordinates.toWorld(level, anchor.getCenter())) < 1.0E-8,
                        "Nearby-player range check used plot coordinates");
                    ServerPlayer player = player(level);
                    Vec3 source = SableCoordinates.toWorld(level, Vec3.atBottomCenterOf(anchor).add(0, 0, 2));
                    player.setPos(source);
                    player.setYRot(25);
                    var instance = DungeonInstanceManager.get(level.getServer()).get(value.instanceId()).orElseThrow();
                    new PlayerReturnManager().captureFromPortal(player, instance, anchor, 0, 2);
                    var saved = PlayerReturnSavedData.get(level.getServer()).get(player.getUUID()).orElseThrow();
                    helper.assertTrue(saved.subLevel().filter(point -> point.id().equals(subLevel.getUniqueId())).isPresent(),
                        "Return data did not retain contraption identity");
                    subLevel.logicalPose().position().add(30, 0, 20);
                    subLevel.logicalPose().orientation().rotationY(Math.toRadians(-43));
                    Vec3 expected = subLevel.logicalPose().transformPosition(saved.subLevel().orElseThrow().localPosition());
                    CompoundTag tag = new CompoundTag();
                    player.saveWithoutId(tag);
                    tag.putString("Dimension", instance.dimensionId().toString());
                    tag.putUUID("LoginPoint", UUID.randomUUID());
                    player.load(tag);
                    helper.assertTrue(player.position().distanceToSqr(expected) < 1.0E-6,
                        "Reconnection did not follow the moved contraption: " + player.position() + " expected " + expected);
                    helper.assertFalse(tag.contains("LoginPoint"), "Sable restored an obsolete instance login point");
                    PlayerReturnSavedData.get(level.getServer()).remove(player.getUUID());
                    subLevel.logicalPose().orientation().rotationXYZ(0.4, 0.7, 0.2);
                    center = SableCoordinates.toWorld(level, anchor.getCenter().add(0, 1, 0));
                    helper.assertTrue(portal.intersects(new AABB(center, center).inflate(0.1)),
                        "Tilted portal no longer matches its world-space volume");
                    manager.cancel(value.id(), "Sable regression finished");
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                } finally {
                    SubLevelContainer.getContainer(level).removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
                }
            })
            .thenSucceed();
    }

    @GameTest(templateNamespace = "instancednotinfinite_integration", template = "empty")
    public static void missingInstanceLoginCannotRestoreSableTrackingPoint(GameTestHelper helper) {
        com.cappleapple.instancednotinfinite.gametest.DungeonLifecycleGameTests.missingReturnRecoversBeforeEntityPositionLoads(helper);
    }

    @net.minecraft.gametest.framework.GameTestGenerator
    public static java.util.Collection<net.minecraft.gametest.framework.TestFunction> optionalUnloadTests() {
        if (!net.neoforged.fml.ModList.get().isLoaded("darkdoppelganger")) return java.util.List.of();
        return java.util.List.of(new net.minecraft.gametest.framework.TestFunction(
            "sable_unload", "instancednotinfinite_sable.dark_doppelganger_unload",
            "instancednotinfinite_integration:empty", net.minecraft.world.level.block.Rotation.NONE,
            1000, 0, true, SablePortalGameTests::darkDoppelgangerUnload));
    }

    private static void darkDoppelgangerUnload(GameTestHelper helper) {
        var manager = DungeonInstanceManager.get(helper.getLevel().getServer());
        try {
            var instance = manager.create(ResourceLocation.parse("instancednotinfinite:surface_igloo"));
            ServerLevel dungeon = helper.getLevel().getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, instance.dimensionId()));
            var minionType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals("darkdoppelganger"))
                .map(java.util.Map.Entry::getValue)
                .filter(type -> {
                    var entity = type.create(dungeon);
                    return entity != null && entity.getClass().getName().equals(
                        "net.bandit.darkdoppelganger.entity.DarkDoppelgangerMinionEntity");
                }).findFirst().orElseThrow();
            var minion = minionType.create(dungeon);
            var entry = instance.plan().orElseThrow().entryPosition();
            minion.setPos(entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5);
            minion.setNoGravity(true);
            if (minion instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
            dungeon.addFreshEntity(minion);
            var other = new net.minecraft.world.entity.item.ItemEntity(dungeon,
                entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
            dungeon.addFreshEntity(other);
            Class.forName("net.bandit.darkdoppelganger.event.ServerEvents").getMethod("onWorldUnload",
                net.neoforged.neoforge.event.level.LevelEvent.Unload.class).invoke(null,
                    new net.neoforged.neoforge.event.level.LevelEvent.Unload(dungeon));
            helper.assertTrue(minion.isRemoved(), "Dark Doppelganger unload cleanup was skipped instead of fixed");
            helper.assertFalse(other.isRemoved(), "Unload compatibility removed an unrelated entity");
            manager.delete(instance.id());
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(manager.get(instance.id()).isEmpty(),
                "Waiting for compatible runtime dimension unload")).thenSucceed();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static ServerPlayer player(ServerLevel level) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "ini-sable-return"), false);
        return new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
    }

    private static ServerSubLevel assembleDeck(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(0, 10, 0));
        BlockPos minimum = center.offset(-3, 0, -3);
        BlockPos maximum = center.offset(3, 0, 3);
        for (BlockPos pos : BlockPos.betweenClosed(minimum, maximum)) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        return SubLevelAssemblyHelper.assembleBlocks(level, center, BlockPos.betweenClosed(minimum, maximum),
            new BoundingBox3i(minimum.getX(), minimum.getY(), minimum.getZ(), maximum.getX(), maximum.getY(), maximum.getZ()));
    }
}
