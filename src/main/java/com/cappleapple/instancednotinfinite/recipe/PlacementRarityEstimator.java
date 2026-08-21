package com.cappleapple.instancednotinfinite.recipe;

public final class PlacementRarityEstimator {
    private PlacementRarityEstimator() {
    }

    public static double randomSpread(int spacing, double frequency, int entryWeight, int totalWeight, boolean exclusionZone) {
        if (spacing < 1 || frequency <= 0.0D || entryWeight < 1 || totalWeight < entryWeight) return 1.0D;
        double selectionChance = frequency * entryWeight / totalWeight;
        double chunksPerOccurrence = spacing * (double)spacing / selectionChance;
        double rarity = (Math.log10(chunksPerOccurrence) - 2.0D) / 2.5D;
        if (exclusionZone) rarity += 0.08D;
        return clamp(rarity);
    }

    public static double concentricRings(int distance, int spread, int count) {
        if (distance < 1 || spread < 1 || count < 1) return 0.75D;
        double nominalAreaPerStructure = Math.PI * Math.pow(distance * Math.max(1.0D, count / (double)spread), 2.0D) / count;
        return clamp((Math.log10(Math.max(1.0D, nominalAreaPerStructure)) - 2.0D) / 2.5D + 0.08D);
    }

    public static double combine(double placementRarity, double sizeScore, boolean bossLike) {
        return clamp(placementRarity * 0.86D + sizeScore * 0.09D + (bossLike ? 0.05D : 0.0D));
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
