package com.cappleapple.instancednotinfinite.recipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PoolRecipeMixerTest {
    @Test
    void worldSeedMixIsDeterministicAndBounded() {
        int[] first = indices(12345L, 5);
        int[] repeated = indices(12345L, 5);
        int[] anotherWorld = indices(98765L, 5);

        assertArrayEquals(first, repeated);
        assertFalse(Arrays.equals(first, anotherWorld));
        assertTrue(Arrays.stream(first).allMatch(index -> index >= 0 && index < 5));
    }

    private static int[] indices(long seed, int members) {
        int[] result = new int[9];
        for (int slot = 0; slot < result.length; slot++) {
            result[slot] = PoolRecipeMixer.memberIndex(seed, "idas:rare", slot, members);
        }
        return result;
    }
}
