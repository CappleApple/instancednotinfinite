package com.cappleapple.instancednotinfinite.recipe;

/** World-seed-stable member selection for each of a structure-pool recipe's nine slots. */
public final class PoolRecipeMixer {
    private PoolRecipeMixer() {
    }

    public static int memberIndex(long worldSeed, String poolId, int slot, int memberCount) {
        if (slot < 0 || slot >= 9) throw new IllegalArgumentException("Pool recipe slot must be 0..8");
        if (memberCount <= 0) throw new IllegalArgumentException("Pool recipe requires at least one member");
        long value = worldSeed
            ^ Integer.toUnsignedLong(DeterministicIngredientSelector.stableHash(poolId + "\u0000pool_recipe\u0000" + slot));
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        value ^= value >>> 31;
        return (int)Math.floorMod(value, memberCount);
    }
}
