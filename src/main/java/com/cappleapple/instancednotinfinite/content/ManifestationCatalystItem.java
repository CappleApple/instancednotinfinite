package com.cappleapple.instancednotinfinite.content;

import com.cappleapple.instancednotinfinite.api.DungeonManifestationApi;
import com.cappleapple.instancednotinfinite.api.ManifestationView;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.manifestation.CatalystConsumptionPolicy;
import com.cappleapple.instancednotinfinite.manifestation.DungeonManifestationManager;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationOptions;
import com.cappleapple.instancednotinfinite.instance.InstanceLifecycleOverrides;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

public final class ManifestationCatalystItem extends Item {
    public ManifestationCatalystItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        ManifestationTargetComponent target = stack.get(ModContent.MANIFESTATION_TARGET.get());
        if (target == null) return super.getName(stack);
        return switch (target.kind()) {
            case "dungeon" -> Component.translatable(
                "item.instancednotinfinite.manifestation_catalyst.dungeon",
                DungeonDisplayName.fromPath(target.id().orElseThrow().getPath()));
            case "structure_pool" -> Component.translatable(
                "item.instancednotinfinite.manifestation_catalyst.structure_pool",
                StructurePoolDisplayName.fromId(target.id().orElseThrow()));
            default -> super.getName(stack);
        };
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.cappleapple.instancednotinfinite.client.ClientCatalystRenderer.get();
            }
        });
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }
        ManifestationTargetComponent component = context.getItemInHand().get(ModContent.MANIFESTATION_TARGET.get());
        if (component == null) component = ManifestationTargetComponent.pool();
        Direction face = context.getClickedFace();
        BlockPos origin = context.getClickedPos().relative(face);
        int rotationDegrees = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.normalize(
            Math.round(player.getYRot()));
        try {
            InstanceLifecycleOverrides lifecycle = context.getItemInHand().get(ModContent.INSTANCE_LIFECYCLE.get());
            if (lifecycle == null) lifecycle = InstanceLifecycleOverrides.empty();
            ManifestationView view = DungeonManifestationApi.spawn(
                level, origin, component.target(),
                new ManifestationOptions(rotationDegrees, com.cappleapple.instancednotinfinite.manifestation.AnimationMode.RANDOM_MODE, lifecycle),
                player);
            CatalystConsumptionPolicy policy = ServerConfig.INSTANCE.catalystConsumptionPolicy.get();
            if (policy != CatalystConsumptionPolicy.NEVER && !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
                DungeonManifestationManager.get(level.getServer()).markItemConsumed(view.id(), policy);
            }
            player.sendSystemMessage(Component.literal(
                "Manifesting " + view.dungeonId() + " (" + view.id().toString().substring(0, 8) + ")"));
            return InteractionResult.SUCCESS;
        } catch (Exception exception) {
            player.sendSystemMessage(Component.literal("Cannot manifest dungeon: " + exception.getMessage()));
            return InteractionResult.FAIL;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ManifestationTargetComponent target = stack.get(ModContent.MANIFESTATION_TARGET.get());
        if (target == null) {
            tooltip.add(Component.literal("Pool: configured weighted catalogue").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else if (target.kind().equals("dungeon") && flag.isAdvanced()) {
            tooltip.add(Component.literal("Dungeon: " + target.id().orElseThrow()).withStyle(ChatFormatting.AQUA));
        } else if (target.kind().equals("structure_pool")) {
            tooltip.add(Component.literal("Structure pool: #" + target.id().orElseThrow()).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else if (!target.kind().equals("dungeon")) {
            tooltip.add(Component.literal("Pool: configured weighted catalogue").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            tooltip.add(Component.literal("Use on a block face to manifest").withStyle(ChatFormatting.GRAY));
            InstanceLifecycleOverrides lifecycle = stack.get(ModContent.INSTANCE_LIFECYCLE.get());
            if (lifecycle != null && !lifecycle.isEmpty()) {
                tooltip.add(Component.literal("Open: " + duration(lifecycle.openSeconds())).withStyle(ChatFormatting.DARK_GRAY));
                tooltip.add(Component.literal("After visit: " + duration(lifecycle.postVisitSeconds())).withStyle(ChatFormatting.DARK_GRAY));
                tooltip.add(Component.literal("Forced collapse: " + duration(lifecycle.forceCollapseSeconds())).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    private static String duration(java.util.Optional<Integer> value) {
        return value.map(seconds -> seconds == -1 ? "infinite" : seconds + "s").orElse("server default");
    }
}
