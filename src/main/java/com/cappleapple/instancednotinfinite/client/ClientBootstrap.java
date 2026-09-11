package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.content.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ClientBootstrap {
    private ClientBootstrap() {
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(ManifestationWorldRenderer::render);
        NeoForge.EVENT_BUS.addListener(ClientBootstrap::clientTick);
        NeoForge.EVENT_BUS.addListener(ClientBootstrap::renderFrame);
        NeoForge.EVENT_BUS.addListener(ClientBootstrap::loggingOut);
        modBus.addListener(ClientBootstrap::reloadListeners);
        modBus.addListener(ClientBootstrap::registerRenderers);
        modBus.addListener(ClientBootstrap::registerGuiLayers);
    }

    private static void renderFrame(RenderFrameEvent.Pre event) {
        double elapsed = ClientAnimationTime.advance(System.nanoTime(), Minecraft.getInstance().isPaused());
        ClientManifestationStore.animate(elapsed);
    }

    private static void clientTick(ClientTickEvent.Post event) {
        if (!Minecraft.getInstance().isPaused()) ClientManifestationStore.tick();
        DistantHorizonsClientCompat.tick();
    }
    private static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientManifestationStore.clear();
        ClientAnimationTime.reset();
        ClientDungeonCatalog.clear();
        DistantHorizonsClientCompat.reset();
    }
    private static void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener)resourceManager -> ClientManifestationStore.resourcesReloaded());
    }
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            ModContent.MANIFESTATION_PORTAL_BLOCK_ENTITY.get(), ManifestationPortalBlockEntityRenderer::new);
    }
    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.CROSSHAIR,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                com.cappleapple.instancednotinfinite.InstancedNotInfinite.MOD_ID, "portal_information"),
            PortalHudOverlay::render);
    }
}
