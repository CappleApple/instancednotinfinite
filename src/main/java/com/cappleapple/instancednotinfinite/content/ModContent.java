package com.cappleapple.instancednotinfinite.content;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.instance.InstanceLifecycleOverrides;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(InstancedNotInfinite.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(InstancedNotInfinite.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, InstancedNotInfinite.MOD_ID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, InstancedNotInfinite.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ManifestationTargetComponent>> MANIFESTATION_TARGET =
        DATA_COMPONENTS.registerComponentType("manifestation_target", builder -> builder.persistent(ManifestationTargetComponent.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<InstanceLifecycleOverrides>> INSTANCE_LIFECYCLE =
        DATA_COMPONENTS.registerComponentType("instance_lifecycle", builder -> builder.persistent(InstanceLifecycleOverrides.CODEC));

    public static final DeferredBlock<ManifestationPortalBlock> MANIFESTATION_PORTAL = BLOCKS.registerBlock(
        "manifestation_portal", ManifestationPortalBlock::new,
        BlockBehaviour.Properties.of().noCollission().strength(-1.0F, 3_600_000.0F).lightLevel(state -> 10).noLootTable());

    public static final Supplier<BlockEntityType<ManifestationPortalBlockEntity>> MANIFESTATION_PORTAL_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("manifestation_portal", () ->
            BlockEntityType.Builder.of(ManifestationPortalBlockEntity::new, MANIFESTATION_PORTAL.get()).build(null));

    public static final DeferredItem<ManifestationCatalystItem> MANIFESTATION_CATALYST = ITEMS.registerItem(
        "manifestation_catalyst", ManifestationCatalystItem::new,
        new Item.Properties().stacksTo(16));

    private ModContent() {
    }

    public static void register(IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(ModContent::creativeTabs);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(MANIFESTATION_CATALYST);
        }
    }
}
