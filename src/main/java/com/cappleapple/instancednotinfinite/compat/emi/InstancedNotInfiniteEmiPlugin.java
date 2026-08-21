package com.cappleapple.instancednotinfinite.compat.emi;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.client.ClientDungeonCatalog;
import com.cappleapple.instancednotinfinite.content.ModContent;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;

@EmiEntrypoint
public final class InstancedNotInfiniteEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.setDefaultComparison(ModContent.MANIFESTATION_CATALYST.get(), Comparison.compareComponents());
        var stacks = ClientDungeonCatalog.itemStacks();
        stacks.stream()
            .map(EmiStack::of)
            .forEach(registry::addEmiStack);
        ClientDungeonCatalog.markEmiCatalogApplied();
        InstancedNotInfinite.LOGGER.info("Registered {} targeted dungeon catalysts with EMI", stacks.size());
    }
}
