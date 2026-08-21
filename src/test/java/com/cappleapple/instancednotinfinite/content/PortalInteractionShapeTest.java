package com.cappleapple.instancednotinfinite.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PortalInteractionShapeTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void northFacingPortalUsesWidthAcrossXAndDepthAcrossZ() {
        PortalShapeMath.Bounds bounds = PortalShapeMath.bounds(
            10, 20, 30, 180, 4.0F, 6.0F, 2.0F);

        assertBounds(bounds, 8.5, 18.5, 29.5, 12.5, 24.5, 31.5);
    }

    @Test
    void eastFacingPortalRotatesWidthAndDepth() {
        PortalShapeMath.Bounds bounds = PortalShapeMath.bounds(
            10, 20, 30, 270, 4.0F, 6.0F, 2.0F);

        assertBounds(bounds, 9.5, 18.5, 28.5, 11.5, 24.5, 32.5);
    }

    @Test
    void zeroDepthPortalKeepsAThinUsablePlane() {
        PortalShapeMath.Bounds bounds = PortalShapeMath.bounds(
            0, 0, 0, 0, 2.0F, 3.0F, 0.0F);

        assertEquals(0.125, bounds.maxZ() - bounds.minZ(), EPSILON);
    }

    @Test
    void diagonalPortalGetsACompleteWorldAxisEnvelope() {
        PortalShapeMath.Bounds bounds = PortalShapeMath.bounds(
            10, 20, 30, 45, 4.0F, 6.0F, 2.0F);

        double halfSpan = Math.sqrt(2.0) * 1.5;
        assertBounds(bounds,
            10.5 - halfSpan, 18.5, 30.5 - halfSpan,
            10.5 + halfSpan, 24.5, 30.5 + halfSpan);
    }

    @Test
    void diagonalPortalRejectsEmptyEnvelopeCorner() {
        assertEquals(false, PortalShapeMath.intersects(
            0, 0, 0, 45, 2.0F, 3.0F, 0.0F,
            1.15, 1.0, -0.25, 1.25, 2.0, -0.15));
        assertEquals(true, PortalShapeMath.intersects(
            0, 0, 0, 45, 2.0F, 3.0F, 0.0F,
            0.45, 1.0, 0.45, 0.55, 2.0, 0.55));
    }

    @Test
    void diagonalPortalWidthUsesTheSameSlashAsMinecraftYaw() {
        assertEquals(true, PortalShapeMath.intersects(
            0, 0, 0, 45, 2.0F, 3.0F, 0.0F,
            1.15, 1.0, 1.15, 1.25, 2.0, 1.25));
        assertEquals(false, PortalShapeMath.intersects(
            0, 0, 0, 45, 2.0F, 3.0F, 0.0F,
            -0.25, 1.0, 1.15, -0.15, 2.0, 1.25));
    }

    private static void assertBounds(
        PortalShapeMath.Bounds actual,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        assertEquals(minX, actual.minX(), EPSILON);
        assertEquals(minY, actual.minY(), EPSILON);
        assertEquals(minZ, actual.minZ(), EPSILON);
        assertEquals(maxX, actual.maxX(), EPSILON);
        assertEquals(maxY, actual.maxY(), EPSILON);
        assertEquals(maxZ, actual.maxZ(), EPSILON);
    }
}
