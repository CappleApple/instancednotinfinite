package com.cappleapple.instancednotinfinite.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StructurePoolDisplayNameTest {
    @Test
    void formatsLocalizedModAndTagNamesWithPoolSuffix() {
        assertEquals(
            "Integrated Dungeons and Structures - Rare Structure Pool",
            StructurePoolNameFormatter.format("Integrated Dungeons and Structures", "rare"));
    }
}
