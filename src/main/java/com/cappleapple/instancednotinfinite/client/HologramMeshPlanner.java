package com.cappleapple.instancednotinfinite.client;

import java.util.function.IntPredicate;

/** Small deterministic calculations shared by hologram surface extraction and its tests. */
final class HologramMeshPlanner {
    private HologramMeshPlanner() {
    }

    static int visibleFaceMask(
        int faceCount,
        IntPredicate isFaceOccluded
    ) {
        int mask = 0;
        for (int face = 0; face < faceCount; face++) {
            if (!isFaceOccluded.test(face)) {
                mask |= 1 << face;
            }
        }
        return mask;
    }

    static boolean includes(int faceMask, int face) {
        return (faceMask & 1 << face) != 0;
    }

    static int bucket(double score, int bucketCount) {
        if (bucketCount < 1) throw new IllegalArgumentException("bucketCount must be positive");
        return Math.min(bucketCount - 1, Math.max(0, (int)Math.floor(score * bucketCount)));
    }

    static int bucket(double score, double minimum, double maximum, int bucketCount) {
        double normalized = maximum <= minimum ? 0.0 : (score - minimum) / (maximum - minimum);
        return bucket(normalized, bucketCount);
    }
}
