package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.backend.DynamicLevelBackend;
import com.cappleapple.instancednotinfinite.structure.DungeonStructurePlacer.PreparedStructure;
import java.util.concurrent.Future;

/** The instance is reserved immediately; its expensive layout is handed back by a worker. */
record PendingDungeonCreation(
    DungeonInstance instance,
    DynamicLevelBackend.CreatedLevel created,
    boolean automaticDefinition,
    int biomeFogColor,
    Future<PreparedStructure> preparation
) {
}
