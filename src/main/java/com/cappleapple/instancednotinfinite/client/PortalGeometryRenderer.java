package com.cappleapple.instancednotinfinite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Shared colored portal-plane/volume geometry for entry and return endpoints. */
final class PortalGeometryRenderer {
    private static final float ITEM_PORTAL_SIZE = 0.68F;
    private static final float ITEM_PORTAL_DEPTH = ITEM_PORTAL_SIZE;
    private static final float ITEM_PORTAL_RENDER_SCALE = 0.25F;

    private PortalGeometryRenderer() {
    }

    static void render(
        PoseStack pose,
        MultiBufferSource buffers,
        int rotationDegrees,
        float width,
        float height,
        float depth,
        float minimumWidth,
        float minimumHeight,
        float minimumDepth,
        int innerArgb,
        int outerArgb,
        float growthScale,
        double time,
        float lifetimeFraction,
        float collapseProgress
    ) {
        render(
            pose, buffers, rotationDegrees, width, height, depth, minimumWidth, minimumHeight, minimumDepth,
            innerArgb, outerArgb, growthScale, time, lifetimeFraction, collapseProgress, false);
    }

    private static void render(
        PoseStack pose,
        MultiBufferSource buffers,
        int rotationDegrees,
        float width,
        float height,
        float depth,
        float minimumWidth,
        float minimumHeight,
        float minimumDepth,
        int innerArgb,
        int outerArgb,
        float growthScale,
        double time,
        float lifetimeFraction,
        float collapseProgress,
        boolean ditherFromAllFaces
    ) {
        pose.pushPose();
        // Minecraft yaw increases toward negative X; the pose rotation uses the opposite handedness.
        pose.mulPose(Axis.YP.rotationDegrees(-rotationDegrees));
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        float fullHalfWidth = width * 0.5F * growthScale;
        float fullHalfHeight = height * 0.5F * growthScale;
        float minimumWidthScale = Math.min(1.0F, minimumWidth / Math.max(0.001F, width));
        float minimumHeightScale = Math.min(1.0F, minimumHeight / Math.max(0.001F, height));
        float minimumDepthScale = depth <= 0.0F ? 1.0F : Math.min(1.0F, minimumDepth / depth);
        float timerWidthScale = PortalDitherMath.portalSizeScale(lifetimeFraction, minimumWidthScale);
        float timerHeightScale = PortalDitherMath.portalSizeScale(lifetimeFraction, minimumHeightScale);
        float timerDepthScale = PortalDitherMath.portalSizeScale(lifetimeFraction, minimumDepthScale);
        float halfWidth = fullHalfWidth * timerWidthScale;
        float halfHeight = fullHalfHeight * timerHeightScale;
        float halfDepth = depth * 0.5F * growthScale * timerDepthScale;
        float border = 0.08F * growthScale;
        float pulse = 0.7F + (float)Math.sin(time * 0.12) * 0.2F;

        pose.translate(0.0F, PortalDitherMath.fixedBottomCenterOffset(fullHalfHeight, timerHeightScale), 0.0F);
        int pulsingOuter = withScaledAlpha(outerArgb, pulse);
        float outerHalfDepth = depth <= 0.0F ? 0.0F : halfDepth + border;
        if (collapseProgress <= 0.001F) {
            renderShape(vertices, pose, halfWidth + border, halfHeight + border, outerHalfDepth,
                pulsingOuter);
            renderShape(vertices, pose, halfWidth, halfHeight, halfDepth, innerArgb);
        } else {
            renderDissolvingShape(
                vertices, pose, halfWidth + border, halfHeight + border, outerHalfDepth,
                pulsingOuter, collapseProgress);
            renderDissolvingShape(
                vertices, pose, halfWidth, halfHeight, halfDepth,
                innerArgb, collapseProgress);
        }
        renderDither(
            vertices, pose, halfWidth + border, halfHeight + border,
            ditherFromAllFaces ? outerHalfDepth : halfDepth,
            innerArgb, pulsingOuter, growthScale, time, collapseProgress, ditherFromAllFaces);
        pose.popPose();
    }

    static void renderItemPortal(
        PoseStack pose,
        MultiBufferSource buffers,
        int innerArgb,
        int outerArgb,
        double time,
        float transitionScale,
        float transitionRotationDegrees
    ) {
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        float safeScale = Math.max(0.001F, transitionScale);
        float itemScale = safeScale * ITEM_PORTAL_RENDER_SCALE;
        pose.scale(itemScale, itemScale, itemScale);
        pose.mulPose(Axis.YP.rotationDegrees(18.0F + transitionRotationDegrees));
        render(
            pose, buffers, 0,
            ITEM_PORTAL_SIZE, ITEM_PORTAL_SIZE, ITEM_PORTAL_DEPTH,
            ITEM_PORTAL_SIZE, ITEM_PORTAL_SIZE, ITEM_PORTAL_DEPTH,
            innerArgb, outerArgb, 1.0F, time, 1.0F, 0.0F, true);
        pose.popPose();
    }

    private static void renderDissolvingShape(
        VertexConsumer vertices,
        PoseStack pose,
        float halfWidth,
        float halfHeight,
        float halfDepth,
        int argb,
        float collapseProgress
    ) {
        int columns = 10;
        int rows = 16;
        float cellWidth = halfWidth * 2.0F / columns;
        float cellHeight = halfHeight * 2.0F / rows;
        float gap = Math.min(cellWidth, cellHeight) * 0.055F * Math.min(1.0F, collapseProgress * 8.0F);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                float start = PortalDitherMath.dissolveStart(column, row, columns, rows);
                float disperse = PortalDitherMath.disperseProgress(collapseProgress, start);
                float tileScale = PortalDitherMath.disperseScale(disperse);
                if (tileScale < 0.002F) continue;
                float x = -halfWidth + (column + 0.5F) * cellWidth;
                float y = -halfHeight + (row + 0.5F) * cellHeight;
                float radialLength = Math.max(0.001F, (float)Math.sqrt(x * x + y * y));
                int seed = column * 197 + row * 389;
                float eased = disperse * disperse;
                float travel = eased * (0.18F + noise(seed, 53) * 0.62F);
                x += x / radialLength * travel;
                y += y / radialLength * travel + eased * (0.05F + noise(seed, 59) * 0.28F);
                float side = noise(seed, 61) < 0.5F ? -1.0F : 1.0F;
                float z = side * eased * (0.12F + noise(seed, 67) * 0.58F);
                pose.pushPose();
                pose.translate(x, y, z);
                renderShape(
                    vertices, pose,
                    Math.max(0.001F, (cellWidth * 0.5F - gap) * tileScale),
                    Math.max(0.001F, (cellHeight * 0.5F - gap) * tileScale),
                    halfDepth * tileScale,
                    withScaledAlpha(argb, 1.0F - disperse));
                pose.popPose();
            }
        }
    }

    private static void renderDither(
        VertexConsumer vertices,
        PoseStack pose,
        float edgeHalfWidth,
        float edgeHalfHeight,
        float halfDepth,
        int innerArgb,
        int outerArgb,
        float growthScale,
        double time,
        float collapseProgress,
        boolean ditherFromAllFaces
    ) {
        int fragmentCount = (ditherFromAllFaces ? 42 : 28)
            + Math.round(36.0F * Math.max(0.0F, Math.min(1.0F, collapseProgress)));
        for (int index = 0; index < fragmentCount; index++) {
            double clock = time * 0.055 + noise(index, 3);
            long cycle = (long)Math.floor(clock);
            int seed = (int)(cycle * 37L + index * 101L);
            float age = fraction((float)clock);
            float animationAge = PortalDitherMath.fragmentAnimationAge(age);
            float baseX;
            float baseY;
            float collapse = Math.max(0.0F, Math.min(1.0F, collapseProgress));
            float collapseTravel = collapse * collapse;
            float travel = animationAge * (0.18F + noise(seed, 17) * 0.34F)
                + collapseTravel * (0.20F + noise(seed, 31) * 0.70F);
            float spawnInset = PortalDitherMath.fragmentSpawnInset(age, 0.06F * growthScale);
            float x;
            float y;
            float z;
            if (ditherFromAllFaces) {
                int face = Math.min(5, (int)(noise(seed, 5) * 6.0F));
                float u = noise(seed, 11) * 2.0F - 1.0F;
                float v = noise(seed, 13) * 2.0F - 1.0F;
                float outwardX = 0.0F;
                float outwardY = 0.0F;
                float outwardZ = 0.0F;
                switch (face) {
                    case 0 -> { baseX = -edgeHalfWidth; baseY = u * edgeHalfHeight; z = v * halfDepth; outwardX = -1.0F; }
                    case 1 -> { baseX = edgeHalfWidth; baseY = u * edgeHalfHeight; z = v * halfDepth; outwardX = 1.0F; }
                    case 2 -> { baseX = u * edgeHalfWidth; baseY = -edgeHalfHeight; z = v * halfDepth; outwardY = -1.0F; }
                    case 3 -> { baseX = u * edgeHalfWidth; baseY = edgeHalfHeight; z = v * halfDepth; outwardY = 1.0F; }
                    case 4 -> { baseX = u * edgeHalfWidth; baseY = v * edgeHalfHeight; z = -halfDepth; outwardZ = -1.0F; }
                    default -> { baseX = u * edgeHalfWidth; baseY = v * edgeHalfHeight; z = halfDepth; outwardZ = 1.0F; }
                }
                float offset = travel - spawnInset;
                x = baseX + outwardX * offset;
                y = baseY + outwardY * offset
                    + collapseTravel * (0.06F + noise(seed, 37) * 0.30F);
                z += outwardZ * offset;
            } else {
                int edge = Math.min(3, (int)(noise(seed, 5) * 4.0F));
                float along = noise(seed, 11) * 2.0F - 1.0F;
                switch (edge) {
                    case 0 -> { baseX = -edgeHalfWidth; baseY = along * edgeHalfHeight; }
                    case 1 -> { baseX = edgeHalfWidth; baseY = along * edgeHalfHeight; }
                    case 2 -> { baseX = along * edgeHalfWidth; baseY = -edgeHalfHeight; }
                    default -> { baseX = along * edgeHalfWidth; baseY = edgeHalfHeight; }
                }
                float length = Math.max(0.001F, (float)Math.sqrt(baseX * baseX + baseY * baseY));
                float outwardX = baseX / length;
                float outwardY = baseY / length;
                x = baseX + outwardX * (travel - spawnInset);
                y = baseY + outwardY * (travel - spawnInset)
                    + collapseTravel * (0.06F + noise(seed, 37) * 0.30F);
                float side = noise(seed, 41) < 0.5F ? -1.0F : 1.0F;
                z = (noise(seed, 23) - 0.5F) * Math.max(0.08F, halfDepth * 2.0F)
                    + side * collapseTravel * (0.12F + noise(seed, 47) * 0.62F);
            }
            float size = (0.025F + noise(seed, 29) * 0.115F)
                * PortalDitherMath.fragmentScale(age, collapseProgress) * growthScale;
            if (size < 0.002F) continue;
            float fragmentAlpha = PortalDitherMath.fragmentAlpha(age, collapseProgress);
            int fadingOuter = withScaledAlpha(outerArgb, fragmentAlpha);
            int fadingInner = withScaledAlpha(innerArgb, fragmentAlpha);
            pose.pushPose();
            pose.translate(x, y, z);
            renderShape(vertices, pose, size, size, size, fadingOuter);
            renderShape(vertices, pose, size * 0.62F, size * 0.62F, size * 0.62F, fadingInner);
            pose.popPose();
        }
    }

    private static void renderShape(
        VertexConsumer vertices,
        PoseStack pose,
        float halfWidth,
        float halfHeight,
        float halfDepth,
        int argb
    ) {
        if (halfDepth <= 0.0001F) {
            face(vertices, pose,
                -halfWidth, -halfHeight, 0.0F,
                halfWidth, -halfHeight, 0.0F,
                halfWidth, halfHeight, 0.0F,
                -halfWidth, halfHeight, 0.0F,
                argb);
            return;
        }
        float x0 = -halfWidth;
        float x1 = halfWidth;
        float y0 = -halfHeight;
        float y1 = halfHeight;
        float z0 = -halfDepth;
        float z1 = halfDepth;
        face(vertices, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, argb);
        face(vertices, pose, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, argb);
        face(vertices, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, argb);
        face(vertices, pose, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, argb);
        face(vertices, pose, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, argb);
        face(vertices, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, argb);
    }

    private static void face(
        VertexConsumer vertices,
        PoseStack pose,
        float ax, float ay, float az,
        float bx, float by, float bz,
        float cx, float cy, float cz,
        float dx, float dy, float dz,
        int argb
    ) {
        var matrix = pose.last().pose();
        int alpha = argb >>> 24;
        int red = argb >>> 16 & 0xFF;
        int green = argb >>> 8 & 0xFF;
        int blue = argb & 0xFF;
        vertices.addVertex(matrix, ax, ay, az).setColor(red, green, blue, alpha);
        vertices.addVertex(matrix, bx, by, bz).setColor(red, green, blue, alpha);
        vertices.addVertex(matrix, cx, cy, cz).setColor(red, green, blue, alpha);
        vertices.addVertex(matrix, dx, dy, dz).setColor(red, green, blue, alpha);
    }

    private static int withScaledAlpha(int argb, float scale) {
        int alpha = Math.max(0, Math.min(255, Math.round((argb >>> 24) * scale)));
        return alpha << 24 | argb & 0x00FF_FFFF;
    }

    private static float fraction(float value) {
        return value - (float)Math.floor(value);
    }

    private static float noise(int value, int salt) {
        int mixed = value * 0x45D9F3B + salt * 0x119DE1F3;
        mixed = (mixed ^ (mixed >>> 16)) * 0x45D9F3B;
        mixed ^= mixed >>> 16;
        return (mixed & 0x00FF_FFFF) / (float)0x0100_0000;
    }
}
