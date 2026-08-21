package com.cappleapple.instancednotinfinite.terrain;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DominantFoundationLevelTest {
    @Test
    void ignoresSparseHangingGeometryBelowMainFootprint() {
        Map<Integer, Integer> layers = new java.util.HashMap<>();
        layers.put(49, 61);
        layers.put(50, 61);
        layers.put(60, 61);
        for (int y = 61; y < 70; y++) {
            layers.put(y, 4_801);
        }
        layers.put(70, 3_200);
        layers.put(71, 890);

        DominantFoundationLevel.FoundationSpan foundation = DominantFoundationLevel.inferSpan(layers).orElseThrow();
        assertEquals(61, foundation.baseY());
        assertEquals(70, foundation.topY());
    }

    @Test
    void declinesToGuessForNarrowUnsupportedGeometry() {
        assertTrue(DominantFoundationLevel.infer(Map.of(49, 3, 60, 12, 80, 20)).isEmpty());
    }
}
