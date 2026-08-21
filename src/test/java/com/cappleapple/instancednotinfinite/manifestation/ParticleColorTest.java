package com.cappleapple.instancednotinfinite.manifestation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParticleColorTest {
    @Test
    void parsesRgbHex() {
        assertTrue(ParticleColor.isValid("#2AAAFF"));
        assertEquals(0x2AAAFF, ParticleColor.parseRgb("#2AAAFF"));
    }

    @Test
    void rejectsMissingHashAlphaAndInvalidDigits() {
        assertFalse(ParticleColor.isValid("2AAAFF"));
        assertFalse(ParticleColor.isValid("#2AAAFF73"));
        assertFalse(ParticleColor.isValid("#ZZAAFF"));
        assertThrows(IllegalArgumentException.class, () -> ParticleColor.parseRgb("#2AAAFF73"));
    }
}
