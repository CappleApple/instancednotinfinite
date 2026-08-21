package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.manifestation.PortalRotation;
import java.util.OptionalDouble;

/** Ray intersection with an arbitrarily rotated portal box. */
public final class PortalTargetingMath {
    private static final double EPSILON = 1.0E-8;

    private PortalTargetingMath() {
    }

    public static OptionalDouble rayDistance(
        double cameraX,
        double cameraY,
        double cameraZ,
        double lookX,
        double lookY,
        double lookZ,
        double centerX,
        double centerY,
        double centerZ,
        int rotationDegrees,
        float width,
        float height,
        float depth,
        double maximumDistance
    ) {
        if (width <= 0.0F || height <= 0.0F || maximumDistance < 0.0) return OptionalDouble.empty();
        double tangentX = PortalRotation.tangentX(rotationDegrees);
        double tangentZ = PortalRotation.tangentZ(rotationDegrees);
        double normalX = PortalRotation.normalX(rotationDegrees);
        double normalZ = PortalRotation.normalZ(rotationDegrees);
        double offsetX = cameraX - centerX;
        double offsetY = cameraY - centerY;
        double offsetZ = cameraZ - centerZ;
        double[] origins = {
            offsetX * tangentX + offsetZ * tangentZ,
            offsetY,
            offsetX * normalX + offsetZ * normalZ
        };
        double[] directions = {
            lookX * tangentX + lookZ * tangentZ,
            lookY,
            lookX * normalX + lookZ * normalZ
        };
        double[] extents = {
            width * 0.5,
            height * 0.5,
            Math.max(1.0 / 16.0, depth * 0.5)
        };
        double minimum = 0.0;
        double maximum = maximumDistance;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(directions[axis]) < EPSILON) {
                if (Math.abs(origins[axis]) > extents[axis]) return OptionalDouble.empty();
                continue;
            }
            double first = (-extents[axis] - origins[axis]) / directions[axis];
            double second = (extents[axis] - origins[axis]) / directions[axis];
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            minimum = Math.max(minimum, first);
            maximum = Math.min(maximum, second);
            if (minimum > maximum) return OptionalDouble.empty();
        }
        return OptionalDouble.of(minimum);
    }
}
