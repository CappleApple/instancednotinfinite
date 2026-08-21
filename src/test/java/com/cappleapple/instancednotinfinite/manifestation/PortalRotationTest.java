package com.cappleapple.instancednotinfinite.manifestation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PortalRotationTest {
    @Test
    void preservesWholeDegreeNonCardinalAngles() {
        assertEquals(37, PortalRotation.normalize(37));
        assertEquals(359, PortalRotation.normalize(-1));
        assertEquals(1, PortalRotation.normalize(361));
    }

    @Test
    void basisMatchesMinecraftPlayerYawAtDiagonalAngles() {
        double diagonal = 1.0 / Math.sqrt(2.0);
        assertEquals(-diagonal, PortalRotation.normalX(45), 1.0E-12);
        assertEquals(diagonal, PortalRotation.normalZ(45), 1.0E-12);
        assertEquals(diagonal, PortalRotation.tangentX(45), 1.0E-12);
        assertEquals(diagonal, PortalRotation.tangentZ(45), 1.0E-12);
    }
}
