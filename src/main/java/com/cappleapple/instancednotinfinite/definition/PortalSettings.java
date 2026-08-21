package com.cappleapple.instancednotinfinite.definition;

import com.cappleapple.instancednotinfinite.manifestation.PortalColor;

/** Optional per-dungeon portal colors supplied by an advanced datapack definition. */
public record PortalSettings(String innerColor, String outerColor) {
    public static final PortalSettings DEFAULT = new PortalSettings(null, null);

    public PortalSettings {
        if (innerColor != null && !PortalColor.isValid(innerColor)) {
            throw new IllegalArgumentException("portal.innerColor must use #RRGGBBAA");
        }
        if (outerColor != null && !PortalColor.isValid(outerColor)) {
            throw new IllegalArgumentException("portal.outerColor must use #RRGGBBAA");
        }
    }
}
