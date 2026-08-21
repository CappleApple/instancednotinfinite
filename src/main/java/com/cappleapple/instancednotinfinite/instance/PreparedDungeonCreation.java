package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.backend.DynamicLevelBackend;
import com.cappleapple.instancednotinfinite.structure.DungeonStructurePlacer.PreparedStructure;
import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;

record PreparedDungeonCreation(
    DungeonInstance instance,
    DynamicLevelBackend.CreatedLevel created,
    PreparedStructure structure,
    GenerationPlan plan,
    boolean automaticDefinition,
    int biomeFogColor
) {
}
