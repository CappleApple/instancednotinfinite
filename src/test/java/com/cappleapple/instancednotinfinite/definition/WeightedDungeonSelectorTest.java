package com.cappleapple.instancednotinfinite.definition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedDungeonSelectorTest {
    @Test
    void selectsByWeightInStableResourceIdOrder() {
        String common = "example:common";
        String rare = "example:rare";
        Map<String, DungeonDefinition> definitions = new LinkedHashMap<>();
        definitions.put(rare, definition(rare, 1));
        definitions.put(common, definition(common, 3));

        assertEquals(common, WeightedDungeonSelector.select(definitions, 0L).orElseThrow());
        assertEquals(common, WeightedDungeonSelector.select(definitions, 2L).orElseThrow());
        assertEquals(rare, WeightedDungeonSelector.select(definitions, 3L).orElseThrow());
        assertEquals(rare, WeightedDungeonSelector.select(definitions, -1L).orElseThrow());
    }

    @Test
    void emptyPoolReturnsEmpty() {
        assertTrue(WeightedDungeonSelector.select(Map.<String, DungeonDefinition>of(), 1L).isEmpty());
    }

    private static DungeonDefinition definition(String id, int weight) {
        return new DungeonDefinition(
            id, 1, "minecraft:igloo/top", StructureKind.TEMPLATE, weight,
            List.of(new BiomeRule("minecraft:plains", 1)), new HeightContext(0, 64),
            EnvironmentType.SURFACE, null, new TerrainSettings(16, 16, 64), PortalSettings.DEFAULT,
            new EntryPoint(0, 1, 0, 0.0F, 0.0F), PlacementMode.DIRECT,
            DecorationMode.SAFE, true, ReentryPolicy.WHILE_ACTIVE);
    }
}
