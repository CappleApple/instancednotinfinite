package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.content.ManifestationTargetComponent;
import com.cappleapple.instancednotinfinite.content.ModContent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ClientCatalystRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation FALLBACK = ResourceLocation.fromNamespaceAndPath(
        InstancedNotInfinite.MOD_ID, "textures/item/manifestation_catalyst.png");
    private static final ClientDungeonCatalog.PortalPreviewColors FALLBACK_PORTAL =
        new ClientDungeonCatalog.PortalPreviewColors(FALLBACK, 0xF5010104, 0x732AAAFF);
    private static ClientCatalystRenderer instance;

    private ClientCatalystRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static ClientCatalystRenderer get() {
        if (instance == null) instance = new ClientCatalystRenderer();
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        ManifestationTargetComponent target = stack.get(ModContent.MANIFESTATION_TARGET.get());
        if (context != ItemDisplayContext.GUI) {
            renderWorldItem(target, pose, buffer, light, overlay);
            return;
        }
        if (target != null && target.kind().equals("structure_pool")) {
            renderStructurePool(target.id().orElseThrow(), context, pose, buffer, light, overlay);
            return;
        }
        ResourceLocation texture = target == null
            ? FALLBACK
            : target.id().flatMap(DungeonIconCache::request).orElse(FALLBACK);
        renderFlatFallback(texture, pose, buffer, light, overlay);
    }

    private static void renderWorldItem(
        ManifestationTargetComponent target,
        PoseStack pose,
        MultiBufferSource buffer,
        int light,
        int overlay
    ) {
        if (target == null || target.id().isEmpty()) {
            renderPortalItem(target, pose, buffer);
            return;
        }
        ResourceLocation targetId = target.id().orElseThrow();
        if (target.kind().equals("dungeon")) {
            boolean rendered = DungeonIconCache.requestMiniature(targetId).map(miniature -> {
                DungeonMiniatureRenderer.renderItem(miniature, pose);
                return true;
            }).orElse(false);
            if (!rendered) renderPortalItem(target, pose, buffer);
            return;
        }
        if (!target.kind().equals("structure_pool")) {
            renderPortalItem(target, pose, buffer);
            return;
        }

        var members = ClientDungeonCatalog.structurePoolMembers(targetId).stream().distinct().sorted().toList();
        if (members.isEmpty()) {
            renderPortalItem(target, pose, buffer);
            return;
        }
        members.forEach(DungeonIconCache::requestMiniature);
        long elapsed = Util.getMillis();
        long intervalMillis = ServerConfig.INSTANCE.poolItemSwapIntervalSeconds.get() * 1_000L;
        PoolCatalystAnimation.ModelFrame frame = PoolCatalystAnimation.modelFrame(
            elapsed, members.size(), intervalMillis);
        var miniature = DungeonIconCache.requestMiniature(members.get(frame.index()));
        if (miniature.isPresent()) {
            DungeonMiniatureRenderer.renderItem(
                miniature.orElseThrow(), pose, frame.scale(), frame.rotationDegrees());
        } else {
            var colors = ClientDungeonCatalog.portalPreview(members.get(frame.index())).orElse(FALLBACK_PORTAL);
            renderPortal(colors, pose, buffer, elapsed, frame.scale(), frame.rotationDegrees());
        }
    }

    private static void renderPortalItem(
        ManifestationTargetComponent target,
        PoseStack pose,
        MultiBufferSource buffer
    ) {
        java.util.List<ClientDungeonCatalog.PortalPreviewColors> previews;
        if (target == null) {
            previews = ClientDungeonCatalog.portalPreviews();
        } else if (target.kind().equals("dungeon")) {
            previews = target.id().flatMap(ClientDungeonCatalog::portalPreview).stream().toList();
        } else if (target.kind().equals("structure_pool")) {
            previews = target.id()
                .map(ClientDungeonCatalog::structurePoolMembers)
                .map(ClientDungeonCatalog::portalPreviews)
                .orElse(java.util.List.of());
        } else {
            previews = ClientDungeonCatalog.portalPreviews();
        }
        if (previews.isEmpty()) previews = java.util.List.of(FALLBACK_PORTAL);

        long elapsed = Util.getMillis();
        int index = 0;
        float scale = 1.0F;
        float rotation = 0.0F;
        if (previews.size() > 1) {
            long intervalMillis = ServerConfig.INSTANCE.poolItemSwapIntervalSeconds.get() * 1_000L;
            PoolCatalystAnimation.ModelFrame frame = PoolCatalystAnimation.modelFrame(
                elapsed, previews.size(), intervalMillis);
            index = frame.index();
            scale = frame.scale();
            rotation = frame.rotationDegrees();
        }
        renderPortal(previews.get(index), pose, buffer, elapsed, scale, rotation);
    }

    private static void renderPortal(
        ClientDungeonCatalog.PortalPreviewColors colors,
        PoseStack pose,
        MultiBufferSource buffer,
        long elapsed,
        float scale,
        float rotation
    ) {
        PortalGeometryRenderer.renderItemPortal(
            pose, buffer, colors.innerColor(), colors.outerColor(), elapsed / 50.0, scale, rotation);
    }

    private static void renderStructurePool(
        ResourceLocation poolId,
        ItemDisplayContext context,
        PoseStack pose,
        MultiBufferSource buffer,
        int light,
        int overlay
    ) {
        var members = ClientDungeonCatalog.structurePoolMembers(poolId).stream().distinct().sorted().toList();
        long elapsed = Util.getMillis();
        long swapIntervalMillis = ServerConfig.INSTANCE.poolItemSwapIntervalSeconds.get() * 1_000L;
        if (members.isEmpty()) {
            renderFlatFallback(FALLBACK, pose, buffer, light, overlay);
            return;
        }
        var icons = members.stream()
            .map(member -> DungeonIconCache.request(member).orElse(FALLBACK))
            .toList();
        PoolCatalystAnimation.IconFrame frame = PoolCatalystAnimation.iconFrame(
            elapsed, members.size(), swapIntervalMillis);
        renderFlatFallback(icons.get(frame.currentIndex()), pose, buffer, light, overlay, 1.0F - frame.blend());
        if (frame.blend() > 0.0F && frame.nextIndex() != frame.currentIndex()) {
            renderFlatFallback(icons.get(frame.nextIndex()), pose, buffer, light, overlay, frame.blend());
        }
    }

    private static void renderFlatFallback(
        ResourceLocation texture,
        PoseStack pose,
        MultiBufferSource buffer,
        int light,
        int overlay
    ) {
        renderFlatFallback(texture, pose, buffer, light, overlay, 1.0F);
    }

    private static void renderFlatFallback(
        ResourceLocation texture,
        PoseStack pose,
        MultiBufferSource buffer,
        int light,
        int overlay,
        float alpha
    ) {
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityTranslucent(texture));
        var matrix = pose.last().pose();
        int alphaByte = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        vertices.addVertex(matrix, 0, 0, 0).setColor(255, 255, 255, alphaByte).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        vertices.addVertex(matrix, 1, 0, 0).setColor(255, 255, 255, alphaByte).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        vertices.addVertex(matrix, 1, 1, 0).setColor(255, 255, 255, alphaByte).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        vertices.addVertex(matrix, 0, 1, 0).setColor(255, 255, 255, alphaByte).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
    }

}
