package com.cappleapple.instancednotinfinite.client;

/** Pure projected-bounds math for fitting a rotated cuboid into an orthographic icon. */
final class MiniatureProjection {
    private MiniatureProjection() {
    }

    static double fit(
        double sizeX,
        double sizeY,
        double sizeZ,
        double pitchDegrees,
        double yawDegrees,
        double availableWidth,
        double availableHeight
    ) {
        ProjectedBounds bounds = bounds(sizeX, sizeY, sizeZ, pitchDegrees, yawDegrees);
        return Math.min(availableWidth / bounds.width(), availableHeight / bounds.height());
    }

    static ProjectedBounds bounds(
        double sizeX,
        double sizeY,
        double sizeZ,
        double pitchDegrees,
        double yawDegrees
    ) {
        double x = Math.max(1.0, sizeX);
        double y = Math.max(1.0, sizeY);
        double z = Math.max(1.0, sizeZ);
        double pitch = Math.toRadians(pitchDegrees);
        double yaw = Math.toRadians(yawDegrees);
        double sinPitch = Math.sin(pitch);
        double cosPitch = Math.cos(pitch);
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);

        double width = Math.abs(cosYaw) * x + Math.abs(sinYaw) * z;
        double height = Math.abs(sinPitch * sinYaw) * x
            + Math.abs(cosPitch) * y
            + Math.abs(sinPitch * cosYaw) * z;
        double depth = Math.abs(cosPitch * sinYaw) * x
            + Math.abs(sinPitch) * y
            + Math.abs(cosPitch * cosYaw) * z;
        return new ProjectedBounds(width, height, depth);
    }

    record ProjectedBounds(double width, double height, double depth) {
    }
}
