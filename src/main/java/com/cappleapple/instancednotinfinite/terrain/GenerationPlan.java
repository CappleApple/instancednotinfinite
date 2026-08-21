package com.cappleapple.instancednotinfinite.terrain;

import com.cappleapple.instancednotinfinite.definition.DungeonDefinition;
import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record GenerationPlan(
    long seed,
    DungeonDefinition definition,
    BoundingBox structureBounds,
    BoundingBox guaranteedBounds,
    BoundingBox envelopeBounds,
    BlockPos structureOrigin,
    int terrainSurfaceY,
    BlockPos entryPosition,
    float entryYaw
) {
    public static final int ANCHOR_Y = 128;
    public static final int MIN_TERRAIN_Y = -63;
    public static final int MAX_TERRAIN_Y = 319;

    public static GenerationPlan fallback(long seed, DungeonDefinition definition) {
        BoundingBox structure = new BoundingBox(-16, ANCHOR_Y, -16, 15, ANCHOR_Y + 31, 15);
        return fromBounds(seed, definition, structure, new BlockPos(-16, ANCHOR_Y, -16), false, ANCHOR_Y - 1);
    }

    public static GenerationPlan fromBounds(long seed, DungeonDefinition definition, BoundingBox structure, BlockPos origin) {
        return fromBounds(seed, definition, structure, origin, false, structure.minY() - 1);
    }

    public static GenerationPlan fromBounds(
        long seed,
        DungeonDefinition definition,
        BoundingBox structure,
        BlockPos origin,
        boolean automaticEntry
    ) {
        return fromBounds(seed, definition, structure, origin, automaticEntry, structure.minY() - 1);
    }

    public static GenerationPlan fromBounds(
        long seed,
        DungeonDefinition definition,
        BoundingBox structure,
        BlockPos origin,
        boolean automaticEntry,
        int terrainSurfaceY
    ) {
        int horizontal = definition.terrain().horizontalPadding();
        int vertical = definition.terrain().verticalPadding();
        int horizontalFalloff = falloffForPadding(horizontal, 12, 32);
        int verticalFalloff = falloffForPadding(vertical, 8, 24);
        int requiredMinY = structure.minY() - vertical - verticalFalloff;
        int requiredMaxY = structure.maxY() + vertical + verticalFalloff;
        boolean customTerrain = definition.environment() == EnvironmentType.CUSTOM;
        if (customTerrain && (requiredMinY < MIN_TERRAIN_Y || requiredMaxY > MAX_TERRAIN_Y)) {
            throw new IllegalArgumentException(
                "structure plus vertical padding and falloff requires Y " + requiredMinY + ".." + requiredMaxY
                    + " but the instance terrain range is " + MIN_TERRAIN_Y + ".." + MAX_TERRAIN_Y);
        }
        BoundingBox guaranteed = new BoundingBox(
            structure.minX() - horizontal,
            Math.max(MIN_TERRAIN_Y, structure.minY() - vertical),
            structure.minZ() - horizontal,
            structure.maxX() + horizontal,
            Math.min(MAX_TERRAIN_Y, structure.maxY() + vertical),
            structure.maxZ() + horizontal);
        BoundingBox envelope = new BoundingBox(
            guaranteed.minX() - horizontalFalloff,
            usesGroundedTerrain(definition.environment())
                ? MIN_TERRAIN_Y
                : Math.max(MIN_TERRAIN_Y, guaranteed.minY() - verticalFalloff),
            guaranteed.minZ() - horizontalFalloff,
            guaranteed.maxX() + horizontalFalloff,
            Math.min(MAX_TERRAIN_Y, guaranteed.maxY() + verticalFalloff),
            guaranteed.maxZ() + horizontalFalloff);
        int radius = Math.max(
            Math.max(Math.abs(envelope.minX()), Math.abs(envelope.maxX())),
            Math.max(Math.abs(envelope.minZ()), Math.abs(envelope.maxZ())));
        if (radius > definition.terrain().maximumRadius()) {
            throw new IllegalArgumentException(
                "structure plus padding requires radius " + radius + " but terrain.maximumRadius is " + definition.terrain().maximumRadius());
        }
        BlockPos entry = automaticEntry
            ? new BlockPos(
                structure.minX() + structure.getXSpan() / 2,
                usesSurfaceApproach(definition.environment())
                    ? terrainSurfaceY + 1
                    : structure.minY() + structure.getYSpan() / 2,
                structure.minZ() + structure.getZSpan() / 2)
            : origin.offset(definition.entry().x(), definition.entry().y(), definition.entry().z());
        return new GenerationPlan(
            seed, definition, structure, guaranteed, envelope, origin.immutable(), terrainSurfaceY, entry.immutable(), definition.entry().yaw());
    }

    public static boolean usesSurfaceApproach(EnvironmentType environment) {
        return environment == EnvironmentType.SURFACE
            || environment == EnvironmentType.FLOATING_ISLAND
            || environment == EnvironmentType.OCEAN_SURFACE
            || environment == EnvironmentType.NETHER_LIKE
            || environment == EnvironmentType.END_LIKE;
    }

    public static boolean usesUndergroundApproach(EnvironmentType environment) {
        return environment == EnvironmentType.UNDERGROUND || environment == EnvironmentType.CAVE;
    }

    public static boolean usesGroundedTerrain(EnvironmentType environment) {
        return environment == EnvironmentType.SURFACE || environment == EnvironmentType.NETHER_LIKE;
    }

    /** Prevents a fallback terrain height from burying a grounded structure all the way past its roof. */
    public static int seatGroundedSurface(EnvironmentType environment, BoundingBox structure, int generatedSurfaceY) {
        return seatGroundedSurface(environment, structure.minY(), structure.maxY(), generatedSurfaceY);
    }

    public static int seatGroundedSurface(
        EnvironmentType environment,
        int structureMinY,
        int structureMaxY,
        int generatedSurfaceY
    ) {
        return TerrainSurfaceSeating.seat(environment, structureMinY, structureMaxY, generatedSurfaceY);
    }

    public static int seatGroundedSurface(
        EnvironmentType environment,
        int structureMinY,
        int structureMaxY,
        int foundationY,
        int generatedSurfaceY
    ) {
        return TerrainSurfaceSeating.seat(environment, structureMinY, structureMaxY, foundationY, generatedSurfaceY);
    }

    public static int verticalFalloffForPadding(int padding) {
        return falloffForPadding(padding, 8, 24);
    }

    private static int falloffForPadding(int padding, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, Math.max(1, padding) / 2));
    }
}
