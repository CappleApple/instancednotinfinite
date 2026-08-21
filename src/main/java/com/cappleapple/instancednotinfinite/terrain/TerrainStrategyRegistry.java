package com.cappleapple.instancednotinfinite.terrain;

import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import java.util.EnumMap;
import java.util.Map;

public final class TerrainStrategyRegistry {
    private static final Map<EnvironmentType, TerrainEnvelopeStrategy> BUILT_INS = createBuiltIns();

    private TerrainStrategyRegistry() {
    }

    public static TerrainEnvelopeStrategy forEnvironment(EnvironmentType type) {
        TerrainEnvelopeStrategy strategy = BUILT_INS.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("No built-in terrain strategy for " + type);
        }
        return strategy;
    }

    private static Map<EnvironmentType, TerrainEnvelopeStrategy> createBuiltIns() {
        EnumMap<EnvironmentType, TerrainEnvelopeStrategy> strategies = new EnumMap<>(EnvironmentType.class);
        TerrainEnvelopeStrategy island = new SurfaceIslandStrategy();
        TerrainEnvelopeStrategy grounded = new GroundedSurfaceStrategy();
        strategies.put(EnvironmentType.SURFACE, grounded);
        strategies.put(EnvironmentType.FLOATING_ISLAND, island);
        TerrainEnvelopeStrategy enclosed = new EnclosedTerrainStrategy();
        strategies.put(EnvironmentType.UNDERGROUND, enclosed);
        strategies.put(EnvironmentType.CAVE, enclosed);
        strategies.put(EnvironmentType.NETHER_LIKE, grounded);
        strategies.put(EnvironmentType.END_LIKE, island);
        strategies.put(EnvironmentType.OCEAN_SURFACE, new OceanSurfaceStrategy());
        strategies.put(EnvironmentType.UNDERWATER, new UnderwaterStrategy());
        return Map.copyOf(strategies);
    }
}
