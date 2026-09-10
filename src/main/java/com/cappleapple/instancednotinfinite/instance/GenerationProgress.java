package com.cappleapple.instancednotinfinite.instance;

/** Weighted presentation progress; job completion is an explicit lifecycle fact. */
final class GenerationProgress {
    private GenerationProgress() {
    }

    static double fraction(int completedUnits, int totalUnits, boolean complete) {
        if (complete) return 1.0;
        // Multiplication before division can leave even done == total one ULP below 1.
        // Only the completed lifecycle state may report the exact endpoint.
        return Math.min(Math.nextDown(1.0), 0.05 + 0.95 * completedUnits / (double) totalUnits);
    }
}
