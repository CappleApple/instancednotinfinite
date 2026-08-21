package com.cappleapple.instancednotinfinite.manifestation;

import net.minecraft.core.Direction;

/** Integer portal yaw and its horizontal basis vectors. */
public final class PortalRotation {
    private PortalRotation() {
    }

    public static int normalize(int degrees) {
        return Math.floorMod(degrees, 360);
    }

    public static int fromDirection(Direction direction) {
        if (direction == null || !direction.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Portal direction must be horizontal");
        }
        return normalize(Math.round(direction.toYRot()));
    }

    public static Direction nearestDirection(int degrees) {
        return Direction.fromYRot(normalize(degrees));
    }

    public static double normalX(int degrees) {
        return snap(-Math.sin(Math.toRadians(normalize(degrees))));
    }

    public static double normalZ(int degrees) {
        return snap(Math.cos(Math.toRadians(normalize(degrees))));
    }

    public static double tangentX(int degrees) {
        return snap(Math.cos(Math.toRadians(normalize(degrees))));
    }

    public static double tangentZ(int degrees) {
        return snap(Math.sin(Math.toRadians(normalize(degrees))));
    }

    private static double snap(double value) {
        if (Math.abs(value) < 1.0E-12) return 0.0;
        if (Math.abs(value - 1.0) < 1.0E-12) return 1.0;
        if (Math.abs(value + 1.0) < 1.0E-12) return -1.0;
        return value;
    }
}
