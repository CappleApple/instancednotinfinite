package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Built-in fallback panel used when Jade is absent or its integration is disabled. */
public final class PortalHudOverlay {
    private static final ResourceLocation FALLBACK_ICON = ResourceLocation.fromNamespaceAndPath(
        InstancedNotInfinite.MOD_ID, "textures/item/manifestation_catalyst.png");

    private PortalHudOverlay() {
    }

    public static void render(GuiGraphics gui, DeltaTracker partialTick) {
        if (JadeIntegration.active() || !ClientConfig.builtInPortalTooltipsEnabled()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui) return;
        double time = minecraft.level.getGameTime() + partialTick.getGameTimeDeltaPartialTick(true);
        PortalTooltipTarget target = PortalTooltipTarget.find(minecraft, time).orElse(null);
        if (target == null) return;
        if (target.kind() == PortalTooltipTarget.Kind.LOADING) {
            renderLoadingPanel(gui, minecraft, target.detailText(), target.loadingProgress());
            return;
        }

        String name = target.displayName();
        String countdown = target.detailText();
        int textWidth = Math.max(minecraft.font.width(name), minecraft.font.width(countdown));
        int panelWidth = Math.max(142, Math.min(224, textWidth + 46));
        name = minecraft.font.plainSubstrByWidth(name, panelWidth - 46);
        int panelHeight = 42;
        int x = Math.max(4, (gui.guiWidth() - panelWidth) / 2);
        int y = 8;

        gui.fill(x, y, x + panelWidth, y + panelHeight, 0xE0180922);
        gui.fill(x, y, x + panelWidth, y + 2, 0xFF7132A8);
        gui.fill(x, y + panelHeight - 2, x + panelWidth, y + panelHeight, 0xFF3A1658);
        gui.fill(x, y, x + 2, y + panelHeight, 0xFF7132A8);
        gui.fill(x + panelWidth - 2, y, x + panelWidth, y + panelHeight, 0xFF3A1658);
        gui.fill(x + 5, y + 5, x + 37, y + 37, 0xB0000000);

        ResourceLocation icon = DungeonIconCache.request(target.dungeonId()).orElse(FALLBACK_ICON);
        RenderSystem.enableBlend();
        gui.blit(icon, x + 7, y + 7, 0.0F, 0.0F, 28, 28, 28, 28);
        RenderSystem.disableBlend();
        gui.drawString(minecraft.font, name, x + 42, y + 9, 0xFFF4F1F8, true);
        gui.drawString(minecraft.font, countdown, x + 42, y + 24, 0xFFB9ADC4, false);
    }

    private static void renderLoadingPanel(
        GuiGraphics gui,
        Minecraft minecraft,
        String loading,
        float animationProgress
    ) {
        int panelWidth = Math.max(116, minecraft.font.width(loading) + 16);
        int panelHeight = 34;
        int x = Math.max(4, (gui.guiWidth() - panelWidth) / 2);
        int y = 8;

        gui.fill(x, y, x + panelWidth, y + panelHeight, 0xE0180922);
        gui.fill(x, y, x + panelWidth, y + 2, 0xFF7132A8);
        gui.fill(x, y + panelHeight - 2, x + panelWidth, y + panelHeight, 0xFF3A1658);
        gui.fill(x, y, x + 2, y + panelHeight, 0xFF7132A8);
        gui.fill(x + panelWidth - 2, y, x + panelWidth, y + panelHeight, 0xFF3A1658);
        gui.drawString(minecraft.font, loading, x + 8, y + 7, 0xFFF4F1F8, true);

        int barX = x + 8;
        int barY = y + 21;
        int barWidth = panelWidth - 16;
        gui.fill(barX, barY, barX + barWidth, barY + 6, 0xFF281E2D);
        int fillWidth = LoadingProgressMath.filledWidth(animationProgress, barWidth);
        if (fillWidth > 0) gui.fill(barX, barY, barX + fillWidth, barY + 6, 0xFF8D50C4);
        gui.fill(barX, barY, barX + barWidth, barY + 1, 0xFFB389D8);
    }
}
