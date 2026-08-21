package com.cappleapple.instancednotinfinite.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniatureProjectionTest {
    @Test
    void unrotatedBoundsMatchSourceCuboid() {
        MiniatureProjection.ProjectedBounds bounds = MiniatureProjection.bounds(12, 8, 5, 0, 0);
        assertEquals(12.0, bounds.width(), 1.0E-9);
        assertEquals(8.0, bounds.height(), 1.0E-9);
        assertEquals(5.0, bounds.depth(), 1.0E-9);
    }

    @Test
    void isometricFitKeepsEveryRotatedDimensionInsideTheIcon() {
        double available = 0.86;
        MiniatureProjection.ProjectedBounds bounds = MiniatureProjection.bounds(160, 96, 128, 30, 135);
        double fit = MiniatureProjection.fit(160, 96, 128, 30, 135, available, available);
        assertTrue(bounds.width() * fit <= available + 1.0E-9);
        assertTrue(bounds.height() * fit <= available + 1.0E-9);
        assertTrue(bounds.width() * fit > available * 0.5 || bounds.height() * fit > available * 0.5);
    }

    @Test
    void rotatedCubeUsesSmallerFitThanTheOldUnrotatedRule() {
        double fit = MiniatureProjection.fit(100, 100, 100, 30, 135, 0.86, 0.86);
        assertTrue(fit < 0.86 / 100.0, "The projected diagonal must be accounted for instead of clipping the cube");
    }

    @Test
    void degenerateInputStillProducesFiniteIconGeometry() {
        MiniatureProjection.ProjectedBounds bounds = MiniatureProjection.bounds(0, -1, 0, 30, 135);
        double fit = MiniatureProjection.fit(0, -1, 0, 30, 135, 0.86, 0.86);
        assertTrue(bounds.width() > 0.0);
        assertTrue(bounds.height() > 0.0);
        assertTrue(Double.isFinite(fit) && fit > 0.0);
    }
}
