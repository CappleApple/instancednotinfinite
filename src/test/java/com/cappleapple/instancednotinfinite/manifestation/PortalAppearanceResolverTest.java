package com.cappleapple.instancednotinfinite.manifestation;

import com.cappleapple.instancednotinfinite.definition.PortalSettings;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortalAppearanceResolverTest {
    @Test
    void datapackHexOverridesBiomeFogAndFallbackPerLayer() {
        ResolvedPortalColors colors = PortalAppearanceResolver.resolve(
            new PortalSettings("#01020304", "#AABBCCDD"), OptionalInt.of(0x112233),
            true, true, 0.04, 0.0, 45, "#101010FF", "#202020FF");
        assertEquals(0x04010203, colors.innerColor());
        assertEquals(0xDDAABBCC, colors.outerColor());
    }

    @Test
    void derivesEnabledLayersAndKeepsDisabledLayerFallback() {
        ResolvedPortalColors colors = PortalAppearanceResolver.resolve(
            PortalSettings.DEFAULT, OptionalInt.of(0x123456),
            false, true, 0.04, 0.0, 45, "#010104F5", "#2AAAFF73");
        assertEquals(0xF5010104, colors.innerColor());
        assertEquals(0x73123456, colors.outerColor());
    }

    @Test
    void fallsBackWhenBiomeFogIsUnavailable() {
        ResolvedPortalColors colors = PortalAppearanceResolver.resolve(
            PortalSettings.DEFAULT, OptionalInt.empty(),
            true, true, 0.5, 0.0, 50, "#01020304", "#AABBCCDD");
        assertEquals(0x04010203, colors.innerColor());
        assertEquals(0xDDAABBCC, colors.outerColor());
    }

    @Test
    void shadesBiomeInnerColorTowardWhiteAndUsesFloatingTransparency() {
        ResolvedPortalColors colors = PortalAppearanceResolver.resolve(
            PortalSettings.DEFAULT, OptionalInt.of(0x204060),
            true, false, 0.25, 0.5, 45, "#01020304", "#AABBCCDD");
        assertEquals(0xBF90A0B0, colors.innerColor());
        assertEquals(0xDDAABBCC, colors.outerColor());
    }
}
