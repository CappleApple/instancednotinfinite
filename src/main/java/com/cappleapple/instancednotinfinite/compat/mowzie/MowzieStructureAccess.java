package com.cappleapple.instancednotinfinite.compat.mowzie;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** Exposes the per-structure biome set Mowzie's Mobs computes from its common config. */
public interface MowzieStructureAccess {
    Set<Holder<Biome>> instancednotinfinite$allowedBiomes();
}
