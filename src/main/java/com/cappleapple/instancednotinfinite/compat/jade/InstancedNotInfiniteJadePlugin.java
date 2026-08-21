package com.cappleapple.instancednotinfinite.compat.jade;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.client.ClientDungeonCatalog;
import com.cappleapple.instancednotinfinite.client.JadeIntegration;
import com.cappleapple.instancednotinfinite.client.JadeTargetBridge;
import com.cappleapple.instancednotinfinite.client.PortalTooltipTarget;
import com.cappleapple.instancednotinfinite.config.ClientConfig;
import com.cappleapple.instancednotinfinite.content.ManifestationPortalBlock;
import com.cappleapple.instancednotinfinite.content.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

/** Optional Jade presentation for client-rendered source portals and return portal blocks. */
@WailaPlugin("jade")
public final class InstancedNotInfiniteJadePlugin implements IWailaPlugin {
    private static final PortalProvider PROVIDER = new PortalProvider();

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(PROVIDER, ManifestationPortalBlock.class);
        registration.registerBlockIcon(PROVIDER, ManifestationPortalBlock.class);
        registration.addRayTraceCallback((hitResult, accessor, originalAccessor) -> {
            if (!ClientConfig.jadeIntegrationEnabled()) {
                if (accessor instanceof BlockAccessor blockAccessor
                    && blockAccessor.getBlock() instanceof ManifestationPortalBlock) {
                    return null;
                }
                return accessor;
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null) return accessor;
            PortalTooltipTarget target = JadeTargetBridge.current()
                .orElseGet(() -> JadeTargetBridge.refresh(minecraft).orElse(null));
            if (target == null) return accessor;
            if (accessor instanceof BlockAccessor blockAccessor
                && blockAccessor.getBlock() instanceof ManifestationPortalBlock
                && blockAccessor.getPosition().equals(target.anchor())) {
                return accessor;
            }

            BlockHitResult syntheticHit = new BlockHitResult(
                target.hitLocation(), Direction.UP, target.anchor(), false);
            return registration.blockAccessor()
                .level(minecraft.level)
                .player(minecraft.player)
                .serverData(new CompoundTag())
                .serverConnected(false)
                .showDetails(registration.isShowDetailsPressed())
                .hit(syntheticHit)
                .blockState(ModContent.MANIFESTATION_PORTAL.get().defaultBlockState())
                .fakeBlock(ClientDungeonCatalog.itemStack(target.dungeonId()))
                .build();
        });
    }

    private static final class PortalProvider implements IBlockComponentProvider {
        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            InstancedNotInfinite.MOD_ID, "portal_information");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!JadeIntegration.active()) {
                tooltip.clear();
                return;
            }
            PortalTooltipTarget target = currentTarget();
            if (target == null) return;
            if (!tooltip.replace(JadeIds.CORE_OBJECT_NAME, Component.literal(target.displayName()))) {
                tooltip.add(0, Component.literal(target.displayName()), JadeIds.CORE_OBJECT_NAME);
            }
            tooltip.add(Component.literal(target.detailText()));
            if (target.kind() == PortalTooltipTarget.Kind.LOADING) {
                tooltip.add(IElementHelper.get().progress(target.loadingProgress()));
            }
        }

        @Override
        public IElement getIcon(BlockAccessor accessor, IPluginConfig config, IElement currentIcon) {
            PortalTooltipTarget target = currentTarget();
            return target == null ? currentIcon : IElementHelper.get().item(
                ClientDungeonCatalog.itemStack(target.dungeonId()));
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public boolean isRequired() {
            return true;
        }

        private static PortalTooltipTarget currentTarget() {
            return JadeTargetBridge.current().orElse(null);
        }
    }
}
