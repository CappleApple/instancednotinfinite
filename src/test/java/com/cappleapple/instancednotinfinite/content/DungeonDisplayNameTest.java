package com.cappleapple.instancednotinfinite.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DungeonDisplayNameTest {
    @Test
    void acropolisUsesOnlyItsTitleCasedPath() {
        assertEquals("Acropolis", DungeonDisplayName.fromPath("acropolis"));
    }

    @Test
    void underscoresBecomeSpaces() {
        assertEquals("Malkuth Arena", DungeonDisplayName.fromPath("malkuth_arena"));
        assertEquals("Trial Chambers", DungeonDisplayName.fromPath("trial_chambers"));
    }

    @Test
    void nestedDatapackPathsUseTheStructureLeafName() {
        assertEquals("Ancient Temple", DungeonDisplayName.fromPath("ruins/ancient_temple"));
    }

    @Test
    void otherResourcePathSeparatorsAreHumanized() {
        assertEquals("Sunken City Wing", DungeonDisplayName.fromPath("sunken-city.wing"));
    }
}
