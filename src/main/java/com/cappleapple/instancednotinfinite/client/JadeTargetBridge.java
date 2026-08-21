package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.config.ClientConfig;
import java.util.Optional;
import net.minecraft.client.Minecraft;

/** Authoritative visual target selected by this mod before Jade builds its synthetic accessor. */
public final class JadeTargetBridge {
    private static PortalTooltipTarget current;

    private JadeTargetBridge() {
    }

    public static Optional<PortalTooltipTarget> refresh(Minecraft minecraft) {
        if (!ClientConfig.jadeIntegrationEnabled() || minecraft.level == null || minecraft.player == null) {
            current = null;
            return Optional.empty();
        }
        current = PortalTooltipTarget.find(minecraft, minecraft.level.getGameTime()).orElse(null);
        return Optional.ofNullable(current);
    }

    public static Optional<PortalTooltipTarget> current() {
        return Optional.ofNullable(current);
    }

    public static void clear() {
        current = null;
    }
}
