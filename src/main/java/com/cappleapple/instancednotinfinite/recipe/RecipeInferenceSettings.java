package com.cappleapple.instancednotinfinite.recipe;

import com.cappleapple.instancednotinfinite.config.ServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public record RecipeInferenceSettings(
    boolean automaticRecipeGeneration,
    boolean paletteInference,
    boolean biomeInference,
    boolean dimensionInference,
    boolean nameInference,
    boolean rarityInference
) {
    public static RecipeInferenceSettings configured() {
        ServerConfig config = ServerConfig.INSTANCE;
        return new RecipeInferenceSettings(
            value(config.automaticRecipeGeneration), value(config.paletteInference), value(config.biomeInference),
            value(config.dimensionInference), value(config.nameInference), value(config.rarityInference));
    }

    private static boolean value(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return value.getDefault();
        }
    }
}
