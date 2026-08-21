package com.cappleapple.instancednotinfinite.content;

/** Converts a dungeon resource path into the short name shown on targeted catalysts. */
public final class DungeonDisplayName {
    private DungeonDisplayName() {
    }

    public static String fromPath(String path) {
        int leafStart = path.lastIndexOf('/') + 1;
        String leaf = path.substring(leafStart);
        StringBuilder result = new StringBuilder(leaf.length());
        boolean capitalize = true;
        for (int index = 0; index < leaf.length(); index++) {
            char character = leaf.charAt(index);
            if (character == '_' || character == '-' || character == '.') {
                if (!result.isEmpty() && result.charAt(result.length() - 1) != ' ') result.append(' ');
                capitalize = true;
                continue;
            }
            result.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = false;
        }
        return result.toString().trim();
    }
}
