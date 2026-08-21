package com.cappleapple.instancednotinfinite.manifestation;

import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.definition.DungeonDefinition;
import com.cappleapple.instancednotinfinite.definition.BiomeSelector;
import com.cappleapple.instancednotinfinite.definition.PortalSettings;
import com.cappleapple.instancednotinfinite.definition.ResolutionException;
import java.util.OptionalInt;
import net.minecraft.core.RegistryAccess;

/** Applies datapack, selected-biome fog, then global-fallback portal color precedence. */
public final class PortalAppearanceResolver {
    private PortalAppearanceResolver() {
    }

    public static ResolvedPortalColors configured(DungeonDefinition definition, OptionalInt biomeFogRgb) {
        return resolve(
            definition.portal(), biomeFogRgb,
            ServerConfig.INSTANCE.derivePortalInnerColorFromBiome.get(),
            ServerConfig.INSTANCE.derivePortalOuterColorFromBiomeFog.get(),
            ServerConfig.INSTANCE.portalInnerTransparency.get(),
            ServerConfig.INSTANCE.portalInnerBiomeBrightness.get(),
            ServerConfig.INSTANCE.portalOuterOpacityPercent.get(),
            ServerConfig.INSTANCE.portalInnerColor.get(),
            ServerConfig.INSTANCE.portalOuterColor.get());
    }

    /** Resolves a stable preview through the same weighted biome and layer precedence used by a real instance. */
    public static ResolvedPortalColors preview(
        RegistryAccess registries,
        DungeonDefinition definition,
        long selectionSeed
    ) throws ResolutionException {
        var biome = BiomeSelector.select(registries, definition, selectionSeed);
        return configured(definition, OptionalInt.of(biome.holder().value().getFogColor()));
    }

    public static ResolvedPortalColors resolve(
        PortalSettings specific,
        OptionalInt biomeFogRgb,
        boolean deriveInner,
        boolean deriveOuter,
        double innerTransparency,
        double innerBiomeBrightness,
        int outerOpacityPercent,
        String fallbackInner,
        String fallbackOuter
    ) {
        return new ResolvedPortalColors(
            resolveInner(specific.innerColor(), biomeFogRgb, deriveInner, innerTransparency, innerBiomeBrightness, fallbackInner),
            resolveOuter(specific.outerColor(), biomeFogRgb, deriveOuter, outerOpacityPercent, fallbackOuter));
    }

    private static int resolveInner(
        String specific,
        OptionalInt biomeFogRgb,
        boolean derive,
        double transparency,
        double relativeBrightness,
        String fallback
    ) {
        if (specific != null) return PortalColor.parseRgba(specific);
        if (derive && biomeFogRgb.isPresent()) {
            int adjusted = PortalColor.adjustBrightness(biomeFogRgb.getAsInt(), relativeBrightness);
            return PortalColor.withTransparency(adjusted, transparency);
        }
        return PortalColor.parseRgba(fallback);
    }

    private static int resolveOuter(
        String specific,
        OptionalInt biomeFogRgb,
        boolean derive,
        int opacityPercent,
        String fallback
    ) {
        if (specific != null) return PortalColor.parseRgba(specific);
        if (derive && biomeFogRgb.isPresent()) {
            return PortalColor.withOpacityPercent(biomeFogRgb.getAsInt(), opacityPercent);
        }
        return PortalColor.parseRgba(fallback);
    }
}
