package com.cappleapple.instancednotinfinite.content;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/** Human-readable category name derived from the tag owner and tag path. */
public final class StructurePoolDisplayName {
    private StructurePoolDisplayName() {
    }

    public static String fromId(ResourceLocation tagId) {
        String modName = ModList.get().getModContainerById(tagId.getNamespace())
            .map(container -> container.getModInfo().getDisplayName())
            .orElseGet(() -> DungeonDisplayName.fromPath(tagId.getNamespace()));
        return format(modName, tagId.getPath());
    }

    public static String format(String localizedModName, String tagPath) {
        return StructurePoolNameFormatter.format(localizedModName, tagPath);
    }
}
