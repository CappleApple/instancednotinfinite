package com.cappleapple.instancednotinfinite.definition;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.OptionalInt;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Reads generic placement evidence exposed by a registered structure's own codec. */
public final class EncodedStructureMetadata {
    private EncodedStructureMetadata() {
    }

    public static OptionalInt absoluteStartHeight(RegistryAccess access, Structure structure) {
        JsonElement encoded = Structure.DIRECT_CODEC
            .encodeStart(RegistryOps.create(JsonOps.INSTANCE, access), structure)
            .result()
            .orElse(null);
        return EncodedStartHeight.absolute(encoded);
    }
}
