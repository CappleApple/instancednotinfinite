package com.cappleapple.instancednotinfinite.manifestation;

import java.util.regex.Pattern;

/** Converts user-facing #RRGGBBAA portal colors to Minecraft's packed ARGB format. */
public final class PortalColor {
    private static final Pattern RGBA_HEX = Pattern.compile("#[0-9a-fA-F]{8}");

    private PortalColor() {
    }

    public static boolean isValid(String value) {
        return value != null && RGBA_HEX.matcher(value).matches();
    }

    public static int parseRgba(String value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Portal color must use #RRGGBBAA: " + value);
        }
        long rgba = Long.parseUnsignedLong(value.substring(1), 16);
        int rgb = (int)(rgba >>> 8);
        int alpha = (int)(rgba & 0xFFL);
        return alpha << 24 | rgb;
    }

    public static int withOpacityPercent(int rgb, int opacityPercent) {
        if (opacityPercent < 0 || opacityPercent > 100) {
            throw new IllegalArgumentException("Portal opacity percentage must be between 0 and 100");
        }
        int alpha = (int)Math.round(opacityPercent * 255.0 / 100.0);
        return alpha << 24 | rgb & 0x00FFFFFF;
    }

    public static int withTransparency(int rgb, double transparency) {
        if (!Double.isFinite(transparency) || transparency < 0.0 || transparency > 1.0) {
            throw new IllegalArgumentException("Portal transparency must be between 0 and 1");
        }
        int alpha = (int)Math.round((1.0 - transparency) * 255.0);
        return alpha << 24 | rgb & 0x00FFFFFF;
    }

    /** Linearly shades an RGB color toward black or white while retaining its relative channel balance. */
    public static int adjustBrightness(int rgb, double relativeBrightness) {
        if (!Double.isFinite(relativeBrightness) || relativeBrightness < -1.0 || relativeBrightness > 1.0) {
            throw new IllegalArgumentException("Portal relative brightness must be between -1 and 1");
        }
        int red = rgb >>> 16 & 0xFF;
        int green = rgb >>> 8 & 0xFF;
        int blue = rgb & 0xFF;
        if (relativeBrightness < 0.0) {
            double scale = 1.0 + relativeBrightness;
            red = (int)Math.round(red * scale);
            green = (int)Math.round(green * scale);
            blue = (int)Math.round(blue * scale);
        } else {
            red = (int)Math.round(red + (255 - red) * relativeBrightness);
            green = (int)Math.round(green + (255 - green) * relativeBrightness);
            blue = (int)Math.round(blue + (255 - blue) * relativeBrightness);
        }
        return red << 16 | green << 8 | blue;
    }

    public static String toRgbaHex(int argb) {
        int alpha = argb >>> 24 & 0xFF;
        int rgb = argb & 0x00FFFFFF;
        return String.format(java.util.Locale.ROOT, "#%06X%02X", rgb, alpha);
    }
}
