package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.config.ClientConfig;
import net.neoforged.fml.ModList;

/** Keeps optional Jade references out of the normal client bootstrap path. */
public final class JadeIntegration {
    private JadeIntegration() {
    }

    public static boolean active() {
        return ModList.get().isLoaded("jade") && ClientConfig.jadeIntegrationEnabled();
    }
}
