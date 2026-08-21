package com.cappleapple.instancednotinfinite.content;

/** Minecraft-independent portal bounds calculation shared with the server interaction shape. */
final class PortalShapeMath {
    private static final double MINIMUM_PLANE_HALF_DEPTH = 1.0 / 16.0;

    private PortalShapeMath() {
    }

    static Bounds bounds(
        int blockX,
        int blockY,
        int blockZ,
        int rotationDegrees,
        float width,
        float height,
        float depth
    ) {
        double halfWidth = Math.max(0.0, width * 0.5);
        double halfHeight = Math.max(0.0, height * 0.5);
        double halfDepth = Math.max(MINIMUM_PLANE_HALF_DEPTH, depth * 0.5);
        double tangentX = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.tangentX(rotationDegrees);
        double tangentZ = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.tangentZ(rotationDegrees);
        double normalX = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.normalX(rotationDegrees);
        double normalZ = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.normalZ(rotationDegrees);
        double halfX = Math.abs(tangentX) * halfWidth + Math.abs(normalX) * halfDepth;
        double halfZ = Math.abs(tangentZ) * halfWidth + Math.abs(normalZ) * halfDepth;
        double centerX = blockX + 0.5;
        double centerY = blockY + 1.5;
        double centerZ = blockZ + 0.5;
        return new Bounds(
            centerX - halfX, centerY - halfHeight, centerZ - halfZ,
            centerX + halfX, centerY + halfHeight, centerZ + halfZ);
    }

    static boolean intersects(
        int blockX,
        int blockY,
        int blockZ,
        int rotationDegrees,
        float width,
        float height,
        float depth,
        double otherMinX,
        double otherMinY,
        double otherMinZ,
        double otherMaxX,
        double otherMaxY,
        double otherMaxZ
    ) {
        double portalCenterX = blockX + 0.5;
        double portalCenterY = blockY + 1.5;
        double portalCenterZ = blockZ + 0.5;
        double otherCenterX = (otherMinX + otherMaxX) * 0.5;
        double otherCenterY = (otherMinY + otherMaxY) * 0.5;
        double otherCenterZ = (otherMinZ + otherMaxZ) * 0.5;
        double otherHalfX = (otherMaxX - otherMinX) * 0.5;
        double otherHalfY = (otherMaxY - otherMinY) * 0.5;
        double otherHalfZ = (otherMaxZ - otherMinZ) * 0.5;
        double halfWidth = Math.max(0.0, width * 0.5);
        double halfHeight = Math.max(0.0, height * 0.5);
        double halfDepth = Math.max(MINIMUM_PLANE_HALF_DEPTH, depth * 0.5);
        double deltaX = otherCenterX - portalCenterX;
        double deltaY = otherCenterY - portalCenterY;
        double deltaZ = otherCenterZ - portalCenterZ;
        if (Math.abs(deltaY) > halfHeight + otherHalfY) return false;

        double tangentX = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.tangentX(rotationDegrees);
        double tangentZ = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.tangentZ(rotationDegrees);
        double normalX = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.normalX(rotationDegrees);
        double normalZ = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.normalZ(rotationDegrees);
        if (Math.abs(deltaX * tangentX + deltaZ * tangentZ)
            > halfWidth + otherHalfX * Math.abs(tangentX) + otherHalfZ * Math.abs(tangentZ)) return false;
        if (Math.abs(deltaX * normalX + deltaZ * normalZ)
            > halfDepth + otherHalfX * Math.abs(normalX) + otherHalfZ * Math.abs(normalZ)) return false;
        if (Math.abs(deltaX)
            > halfWidth * Math.abs(tangentX) + halfDepth * Math.abs(normalX) + otherHalfX) return false;
        return Math.abs(deltaZ)
            <= halfWidth * Math.abs(tangentZ) + halfDepth * Math.abs(normalZ) + otherHalfZ;
    }

    record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }
}
