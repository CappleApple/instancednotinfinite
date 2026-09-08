package com.cappleapple.instancednotinfinite.player;

import com.cappleapple.instancednotinfinite.instance.InstanceId;
import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("instancednotinfinite")
@PrefixGameTestTemplate(false)
public final class ReturnRecoveryGameTests {
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void oldAndContraptionReturnRecordsRoundTrip(GameTestHelper helper) {
        ReturnLocation ordinary = new ReturnLocation(InstanceId.random(), ResourceLocation.parse("minecraft:overworld"),
            12.5, 80, -20.5, 12.25F, -4.5F);
        helper.assertTrue(ReturnLocation.load(ordinary.save()).equals(ordinary), "Legacy return record changed on reload");
        ReturnLocation anchored = new ReturnLocation(ordinary.instanceId(), ordinary.dimension(), ordinary.x(), ordinary.y(),
            ordinary.z(), ordinary.yaw(), ordinary.pitch(), Optional.of(new ReturnLocation.SubLevelReturn(
                UUID.randomUUID(), new Vec3(28_000_000.5, 80, -28_000_000.5), 37.5F)));
        helper.assertTrue(ReturnLocation.load(anchored.save()).equals(anchored), "Contraption identity or local pose was lost on reload");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void normalDimensionLoginKeepsPositionWithStaleReturnRecord(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "ini-normal-login"), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Vec3 expected = Vec3.atBottomCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2)));
        player.setPos(expected);
        CompoundTag tag = new CompoundTag();
        player.saveWithoutId(tag);
        PlayerReturnSavedData.get(server).putIfAbsent(player.getUUID(), new ReturnLocation(InstanceId.random(),
            ResourceLocation.parse("minecraft:overworld"), 4000, 120, 4000, 0, 0));
        player.load(tag);
        new PlayerReturnManager().recoverOnLogin(player);
        helper.assertTrue(player.position().equals(expected), "A stale return record overrode a completed normal-world return");
        helper.assertTrue(PlayerReturnSavedData.get(server).get(player.getUUID()).isEmpty(), "Stale return record was not cleared");
        helper.succeed();
    }
}
