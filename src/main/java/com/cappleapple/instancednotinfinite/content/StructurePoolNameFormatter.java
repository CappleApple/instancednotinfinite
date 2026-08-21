package com.cappleapple.instancednotinfinite.content;

/** Minecraft-free display-name formatting for named structure pools. */
public final class StructurePoolNameFormatter {
    private StructurePoolNameFormatter() {
    }

    public static String format(String localizedModName, String tagPath) {
        return localizedModName + " - " + DungeonDisplayName.fromPath(tagPath) + " Structure Pool";
    }
}
