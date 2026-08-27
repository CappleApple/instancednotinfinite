package com.cappleapple.instancednotinfinite.recipe;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.definition.DungeonDefinitionRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Runs after normal recipes and dungeon definitions, then appends standard shaped recipes. */
public final class PortalRecipeTierReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final RegistryAccess registries;
    private final ReloadableServerResources serverResources;
    private final ICondition.IContext conditionContext;

    public PortalRecipeTierReloadListener(
        RegistryAccess registries,
        ReloadableServerResources serverResources,
        ICondition.IContext conditionContext
    ) {
        super(GSON, "portal_recipe_tiers");
        this.registries = registries;
        this.serverResources = serverResources;
        this.conditionContext = conditionContext;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        PortalRecipeTierParser.Result parsed = PortalRecipeTierParser.parse(resources);
        parsed.diagnostics().forEach(message -> InstancedNotInfinite.LOGGER.warn("Portal recipe tiers: {}", message));
        PortalRecipeGenerationService.INSTANCE.updateTiers(parsed.tiers());
        DungeonDefinitionRegistry.INSTANCE.rebuildAutomatic(this.registries);
        var currentServer = ServerLifecycleHooks.getCurrentServer();
        if (currentServer == null) {
            // Initial data loading precedes the server config and StructureTemplateManager. ServerStarted rebuilds
            // the catalogue and recipes once with both available, avoiding the same expensive analysis twice.
            InstancedNotInfinite.LOGGER.info(
                "Deferred generated portal recipe installation until server start so the persistent cache can use the loaded world config");
        } else {
            PortalRecipeGenerationService.INSTANCE.rebuild(
                this.registries, manager, this.serverResources.getRecipeManager(), currentServer.getStructureManager(),
                ItemTagLookups.staged(this.conditionContext), currentServer.getWorldData().worldGenOptions().seed());
        }
        InstancedNotInfinite.LOGGER.info("Loaded {} portal recipe cost tiers ({} rejected)",
            parsed.tiers().size(), parsed.diagnostics().size());
    }
}
