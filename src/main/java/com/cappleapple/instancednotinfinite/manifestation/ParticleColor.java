package com.cappleapple.instancednotinfinite.manifestation;

import java.util.regex.Pattern;

/** Parses user-facing particle colors without requiring Minecraft classes. */
public final class ParticleColor {
    private static final Pattern RGB_HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    private ParticleColor() {
    }

    public static boolean isValid(String value) {
        return value != null && RGB_HEX.matcher(value).matches();
    }

    public static int parseRgb(String value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Particle color must use #RRGGBB: " + value);
        }
        return Integer.parseUnsignedInt(value.substring(1), 16);
    }
}
