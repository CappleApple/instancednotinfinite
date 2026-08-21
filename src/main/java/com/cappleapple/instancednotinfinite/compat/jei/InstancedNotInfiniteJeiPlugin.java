package com.cappleapple.instancednotinfinite.compat.jei;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.client.ClientDungeonCatalog;
import com.cappleapple.instancednotinfinite.content.ManifestationTargetComponent;
import com.cappleapple.instancednotinfinite.content.ModContent;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class InstancedNotInfiniteJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
        InstancedNotInfinite.MOD_ID, "dungeon_catalog");
    private static IJeiRuntime runtime;
    private static List<ItemStack> registered = List.of();

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ModContent.MANIFESTATION_CATALYST.get(), new ISubtypeInterpreter<>() {
            @Override
            public Object getSubtypeData(ItemStack stack, UidContext context) {
                return stack.get(ModContent.MANIFESTATION_TARGET.get());
            }

            @Override
            @SuppressWarnings("removal")
            public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                ManifestationTargetComponent target = stack.get(ModContent.MANIFESTATION_TARGET.get());
                return target == null ? "" : target.id().map(ResourceLocation::toString).orElse(target.kind());
            }
        });
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        registered = ClientDungeonCatalog.itemStacks();
        registration.addExtraItemStacks(registered);
        InstancedNotInfinite.LOGGER.info("Registered {} targeted dungeon catalysts with JEI", registered.size());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        syncRuntime();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        registered = List.of();
    }

    /** Invoked reflectively only when JEI is installed and the synchronized server catalogue changes. */
    public static void catalogChanged() {
        syncRuntime();
    }

    private static void syncRuntime() {
        if (runtime == null) return;
        if (!registered.isEmpty()) {
            runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, registered);
        }
        registered = ClientDungeonCatalog.itemStacks();
        if (!registered.isEmpty()) {
            runtime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, registered);
        }
        InstancedNotInfinite.LOGGER.info("Synchronized {} targeted dungeon catalysts with JEI", registered.size());
    }
}
