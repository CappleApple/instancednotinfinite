package com.cappleapple.instancednotinfinite.definition;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionParserTest {
    @Test
    void parsesCompleteDefinitionAndWeightedBiomeRules() throws Exception {
        DungeonDefinition definition = DefinitionParser.parse("example:crypt", JsonParser.parseString("""
            {
              "formatVersion": 1,
              "structure": "minecraft:igloo",
              "structureKind": "worldgen",
              "weight": 7,
              "biomes": ["minecraft:plains", {"id":"#minecraft:is_forest","weight":4}],
              "height": {"min":-40,"max":0},
              "environment": {"type":"underground"},
              "terrain": {"horizontalPadding":64,"verticalPadding":24,"maximumRadius":200},
              "portal": {"innerColor":"#11223344","outerColor":"#AABBCCDD"},
              "entry": {"x":2,"y":3,"z":-8,"yaw":90},
              "placement": {"mode":"direct"},
              "decoration": {"mode":"none"},
              "allowNaturalMobSpawning": false,
              "reentry": "until_complete"
            }
            """));

        assertEquals("minecraft:igloo", definition.structure());
        assertEquals(StructureKind.WORLDGEN, definition.structureKind());
        assertEquals(EnvironmentType.UNDERGROUND, definition.environment());
        assertEquals(4, definition.biomes().get(1).weight());
        assertTrue(definition.biomes().get(1).tag());
        assertEquals(-20, definition.height().midpoint());
        assertEquals(ReentryPolicy.UNTIL_COMPLETE, definition.reentry());
        assertEquals("#11223344", definition.portal().innerColor());
        assertEquals("#AABBCCDD", definition.portal().outerColor());
    }

    @Test
    void appliesDocumentedDefaults() throws Exception {
        DungeonDefinition definition = DefinitionParser.parse("example:surface", JsonParser.parseString("""
            {"formatVersion":1,"structure":"minecraft:igloo","biomes":["minecraft:snowy_plains"],"environment":{"type":"surface"}}
            """));
        assertEquals(48, definition.terrain().horizontalPadding());
        assertEquals(32, definition.terrain().verticalPadding());
        assertEquals(DecorationMode.SAFE, definition.decoration());
        assertEquals(ReentryPolicy.WHILE_ACTIVE, definition.reentry());
        assertEquals(PortalSettings.DEFAULT, definition.portal());
    }

    @Test
    void appliesServerConfiguredPaddingDefaults() throws Exception {
        DungeonDefinition definition = DefinitionParser.parse(
            "example:configured", JsonParser.parseString("""
                {"formatVersion":1,"structure":"minecraft:igloo","biomes":["minecraft:plains"],"environment":{"type":"surface"}}
                """), 72, 19);
        assertEquals(72, definition.terrain().horizontalPadding());
        assertEquals(19, definition.terrain().verticalPadding());
    }

    @Test
    void rejectsMalformedAndUnknownDataWithFieldContext() {
        DefinitionException error = assertThrows(DefinitionException.class, () -> DefinitionParser.parse(
            "example:bad", JsonParser.parseString("""
                {"formatVersion":1,"structure":"not an id","biomes":[],"environment":{"type":"surface"}}
                """)));
        assertTrue(error.getMessage().contains("structure") || error.field().equals("$"));
    }

    @Test
    void rejectsMissingBiomeRules() {
        assertThrows(DefinitionException.class, () -> DefinitionParser.parse(
            "example:bad", JsonParser.parseString("""
                {"formatVersion":1,"structure":"minecraft:igloo","environment":{"type":"surface"}}
                """)));
    }

    @Test
    void customEnvironmentRequiresStrategyId() {
        assertThrows(DefinitionException.class, () -> DefinitionParser.parse(
            "example:bad", JsonParser.parseString("""
                {"formatVersion":1,"structure":"minecraft:igloo","biomes":["minecraft:plains"],"environment":{"type":"custom"}}
                """)));
    }

    @Test
    void rejectsPortalColorsWithoutEmbeddedOpacity() {
        DefinitionException error = assertThrows(DefinitionException.class, () -> DefinitionParser.parse(
            "example:bad", JsonParser.parseString("""
                {"formatVersion":1,"structure":"minecraft:igloo","biomes":["minecraft:snowy_plains"],
                 "environment":{"type":"surface"},"portal":{"outerColor":"#AABBCC"}}
                """)));
        assertEquals("portal.outerColor", error.field());
    }
}
