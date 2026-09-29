package com.cappleapple.instancednotinfinite.player;

import com.cappleapple.instancednotinfinite.instance.InstanceId;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("instancednotinfinite")
@PrefixGameTestTemplate(false)
public final class ReturnLocationGameTests {
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void previousModesSurviveSavingAndLegacyRecordsStayUnchanged(GameTestHelper helper) {
        var location = new ReturnLocation(new InstanceId(UUID.randomUUID()),
            ResourceLocation.parse("minecraft:overworld"), 1, 80, 2, 90, 0);
        helper.assertTrue(ReturnLocation.load(location.save()).previousGameMode().isEmpty(), "Legacy mode must be absent");
        for (GameType mode : GameType.values()) {
            var saved = location.withPreviousGameMode(mode);
            helper.assertValueEqual(ReturnLocation.load(saved.save()), saved, "Mode round trip");
        }
        var invalid = location.save();
        invalid.putString("PreviousGameMode", "invalid");
        helper.assertTrue(ReturnLocation.load(invalid).previousGameMode().isEmpty(), "Invalid mode must be ignored");
        helper.succeed();
    }
}
