package com.cappleapple.instancednotinfinite.backend;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class InstanceDimensionIds {
    private static final String PATH_PREFIX = "instances/";

    private InstanceDimensionIds() {
    }

    public static boolean isTemporaryInstance(ResourceKey<Level> dimension) {
        return isTemporaryInstance(dimension.location());
    }

    public static boolean isTemporaryInstance(ResourceLocation dimension) {
        return isTemporaryInstance(dimension.getNamespace(), dimension.getPath());
    }

    static boolean isTemporaryInstance(String namespace, String path) {
        return namespace.equals(InstancedNotInfinite.MOD_ID)
            && path.startsWith(PATH_PREFIX)
            && path.length() > PATH_PREFIX.length();
    }
}
