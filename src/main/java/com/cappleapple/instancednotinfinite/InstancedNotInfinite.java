package com.cappleapple.instancednotinfinite;

import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.config.ClientConfig;
import com.cappleapple.instancednotinfinite.content.ModContent;
import com.cappleapple.instancednotinfinite.network.ManifestationNetwork;
import com.cappleapple.instancednotinfinite.server.ServerEvents;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;

@Mod(InstancedNotInfinite.MOD_ID)
public final class InstancedNotInfinite {
    public static final String MOD_ID = "instancednotinfinite";
    public static final Logger LOGGER = LogUtils.getLogger();

    public InstancedNotInfinite(IEventBus modBus, ModContainer container) {
        ModContent.register(modBus);
        ManifestationNetwork.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.cappleapple.instancednotinfinite.client.ClientBootstrap.register(modBus);
            container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        }
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modBus.addListener(ServerEvents::serverConfigReloaded);
        ServerEvents.register(NeoForge.EVENT_BUS);
    }
}
