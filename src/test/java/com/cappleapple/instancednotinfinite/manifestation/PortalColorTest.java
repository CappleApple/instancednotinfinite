package com.cappleapple.instancednotinfinite.manifestation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PortalColorTest {
    @Test
    void parsesRgbaHexIntoPackedArgb() {
        assertEquals(0x44010203, PortalColor.parseRgba("#01020344"));
        assertEquals(0xFF2AAAFF, PortalColor.parseRgba("#2AAAFFFF"));
    }

    @Test
    void requiresHashAndEmbeddedAlpha() {
        assertTrue(PortalColor.isValid("#abcdef12"));
        assertFalse(PortalColor.isValid("#abcdef"));
        assertFalse(PortalColor.isValid("abcdef12"));
        assertThrows(IllegalArgumentException.class, () -> PortalColor.parseRgba("#nope0000"));
    }

    @Test
    void appliesPercentageOpacityToSampledRgb() {
        assertEquals(0x73112233, PortalColor.withOpacityPercent(0xFF112233, 45));
        assertEquals(0xF5112233, PortalColor.withOpacityPercent(0x00112233, 96));
        assertThrows(IllegalArgumentException.class, () -> PortalColor.withOpacityPercent(0, 101));
    }

    @Test
    void appliesFloatingTransparencyAndRelativeBrightness() {
        assertEquals(0xFF204060, PortalColor.withTransparency(0x204060, 0.0));
        assertEquals(0x80204060, PortalColor.withTransparency(0x204060, 0.5));
        assertEquals(0x00000000, PortalColor.withTransparency(0, 1.0));
        assertEquals(0x102030, PortalColor.adjustBrightness(0x204060, -0.5));
        assertEquals(0x90A0B0, PortalColor.adjustBrightness(0x204060, 0.5));
        assertEquals(0x000000, PortalColor.adjustBrightness(0x204060, -1.0));
        assertEquals(0xFFFFFF, PortalColor.adjustBrightness(0x204060, 1.0));
        assertThrows(IllegalArgumentException.class, () -> PortalColor.withTransparency(0, -0.01));
        assertThrows(IllegalArgumentException.class, () -> PortalColor.adjustBrightness(0, 1.01));
    }

    @Test
    void formatsPackedArgbAsUserFacingRgba() {
        assertEquals("#11223344", PortalColor.toRgbaHex(0x44112233));
        assertEquals("#AABBCCFF", PortalColor.toRgbaHex(0xFFAABBCC));
    }
}
