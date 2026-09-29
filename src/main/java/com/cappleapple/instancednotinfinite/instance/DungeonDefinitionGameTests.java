package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.definition.DefinitionParser;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("instancednotinfinite")
@PrefixGameTestTemplate(false)
public final class DungeonDefinitionGameTests {
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void adventureSettingSurvivesSnapshotAndLegacyLoad(GameTestHelper helper) throws Exception {
        var json = JsonParser.parseString("""
            {"formatVersion":1,"structure":"minecraft:igloo","biomes":["minecraft:snowy_plains"],"environment":{"type":"surface"}}
            """).getAsJsonObject();
        var legacy = DungeonDefinitionNbt.save(DefinitionParser.parse("example:igloo", json));
        helper.assertTrue(DungeonDefinitionNbt.load(legacy).adventureMode() == null, "Old snapshot must not opt in");
        for (boolean mode : new boolean[] {false, true}) {
            json.addProperty("adventureMode", mode);
            var definition = DefinitionParser.parse("example:igloo", json);
            helper.assertValueEqual(DungeonDefinitionNbt.load(DungeonDefinitionNbt.save(definition)), definition,
                "Instance setting round trip");
        }
        helper.succeed();
    }
}
