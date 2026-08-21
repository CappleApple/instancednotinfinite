package com.cappleapple.instancednotinfinite.recipe;

public final class PortalRecipePrecedence {
    private PortalRecipePrecedence() {
    }

    public static RecipeSource choose(boolean datapackRecipe, boolean explicitRecipeOverride, boolean enabled, boolean inferenceFailed) {
        if (datapackRecipe) return RecipeSource.DATAPACK;
        if (!enabled) return RecipeSource.DISABLED;
        if (explicitRecipeOverride) return RecipeSource.EXPLICIT_OVERRIDE;
        return inferenceFailed ? RecipeSource.GENERIC_FALLBACK : RecipeSource.AUTO_GENERATED;
    }
}
