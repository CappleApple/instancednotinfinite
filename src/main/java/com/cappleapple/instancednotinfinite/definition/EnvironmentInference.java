package com.cappleapple.instancednotinfinite.definition;

import java.util.Locale;

public final class EnvironmentInference {
    private static final int DEFAULT_OCEAN_SURFACE_Y = 63;

    private EnvironmentInference() {
    }

    public static Classification classify(Evidence evidence) {
        if (evidence.allNetherBiomes()) {
            return new Classification(EnvironmentType.NETHER_LIKE, "all allowed biomes are Nether biomes");
        }
        if (evidence.allEndBiomes()) {
            return new Classification(EnvironmentType.END_LIKE, "all allowed biomes are End biomes");
        }
        if (evidence.allOceanBiomes()) {
            if (evidence.generationStep().equalsIgnoreCase("top_layer_modification")) {
                return new Classification(
                    EnvironmentType.OCEAN_SURFACE,
                    "all allowed biomes are ocean biomes and the structure modifies the top layer");
            }
            if (evidence.absoluteStartHeight() != null && evidence.absoluteStartHeight() > DEFAULT_OCEAN_SURFACE_Y) {
                return new Classification(
                    EnvironmentType.OCEAN_SURFACE,
                    "all allowed biomes are ocean biomes and the authored absolute start height "
                        + evidence.absoluteStartHeight() + " is above sea level");
            }
            return new Classification(EnvironmentType.UNDERWATER, "all allowed biomes are ocean biomes");
        }

        String adaptation = evidence.terrainAdaptation().toLowerCase(Locale.ROOT);
        if (adaptation.equals("encapsulate")) {
            return new Classification(EnvironmentType.CAVE, "terrain adaptation is ENCAPSULATE");
        }
        if (adaptation.equals("bury")) {
            return new Classification(EnvironmentType.UNDERGROUND, "terrain adaptation is BURY");
        }
        if (evidence.generationStep().equalsIgnoreCase("strongholds")) {
            return new Classification(EnvironmentType.UNDERGROUND, "generation step is STRONGHOLDS");
        }
        if (evidence.generationStep().equalsIgnoreCase("underground_structures")) {
            return new Classification(EnvironmentType.CAVE, "generation step is UNDERGROUND_STRUCTURES");
        }
        return new Classification(EnvironmentType.SURFACE, "no strong underground, aquatic, Nether, or End evidence");
    }

    public record Evidence(
        boolean allNetherBiomes,
        boolean allEndBiomes,
        boolean allOceanBiomes,
        String terrainAdaptation,
        String generationStep,
        Integer absoluteStartHeight
    ) {
    }

    public record Classification(EnvironmentType environment, String reason) {
    }
}
