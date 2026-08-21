package com.cappleapple.instancednotinfinite.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-owned presentation choices that never affect dungeon state or server behavior. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ClientConfig INSTANCE;

    public final ModConfigSpec.BooleanValue jadeIntegration;
    public final ModConfigSpec.BooleanValue builtInPortalTooltips;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        INSTANCE = new ClientConfig(builder);
        SPEC = builder.build();
    }

    private ClientConfig(ModConfigSpec.Builder builder) {
        builder.push("tooltips");
        jadeIntegration = builder.comment(
                "Use Jade for dungeon loading and portal information when Jade is installed.",
                "Disable this to use the built-in portal panel instead, when that panel is enabled.")
            .define("jadeIntegration", ProductionConfigDefaults.JADE_INTEGRATION);
        builtInPortalTooltips = builder.comment(
                "Show Instanced Not Infinite's built-in loading and portal panel when Jade is unavailable or its integration is disabled.")
            .define("builtInPortalTooltips", ProductionConfigDefaults.BUILT_IN_PORTAL_TOOLTIPS);
        builder.pop();
    }

    public static boolean jadeIntegrationEnabled() {
        return configuredOrDefault(INSTANCE.jadeIntegration);
    }

    public static boolean builtInPortalTooltipsEnabled() {
        return configuredOrDefault(INSTANCE.builtInPortalTooltips);
    }

    private static boolean configuredOrDefault(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return value.getDefault();
        }
    }
}
