package com.cappleapple.instancednotinfinite.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicIngredientSelectorTest {
    @Test
    void selectionIsIndependentOfInputOrdering() {
        String dungeon = "cataclysm:burning_arena";
        List<String> first = List.of("minecraft:blackstone", "minecraft:nether_brick", "minecraft:basalt");
        List<String> second = List.of(first.get(2), first.get(0), first.get(1));

        assertEquals(
            DeterministicIngredientSelector.selectString(dungeon, "theme", first),
            DeterministicIngredientSelector.selectString(dungeon, "theme", second));
    }
}
