package com.cappleapple.instancednotinfinite.terrain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

public final class CustomTerrainStrategies {
    private static final Map<ResourceLocation, TerrainEnvelopeStrategy> STRATEGIES = new ConcurrentHashMap<>();

    private CustomTerrainStrategies() {
    }

    public static void register(ResourceLocation id, TerrainEnvelopeStrategy strategy) {
        if (STRATEGIES.putIfAbsent(id, strategy) != null) {
            throw new IllegalStateException("A custom terrain strategy is already registered at " + id);
        }
    }

    public static TerrainEnvelopeStrategy require(ResourceLocation id) {
        TerrainEnvelopeStrategy strategy = STRATEGIES.get(id);
        if (strategy == null) {
            throw new IllegalArgumentException("No custom terrain strategy is registered at " + id);
        }
        return strategy;
    }
}
