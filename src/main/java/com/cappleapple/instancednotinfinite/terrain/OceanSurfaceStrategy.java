package com.cappleapple.instancednotinfinite.terrain;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** A finite ocean whose waterline is the structure's authored world-surface height. */
public final class OceanSurfaceStrategy implements TerrainEnvelopeStrategy {
    @Override
    public BlockState blockAt(GenerationPlan plan, MaterialPalette palette, int x, int y, int z) {
        BoundingBox outer = plan.envelopeBounds();
        BoundingBox basin = plan.guaranteedBounds();
        if (x < outer.minX() || x > outer.maxX() || z < outer.minZ() || z > outer.maxZ()
            || y < outer.minY() || y > outer.maxY()) {
            return Blocks.AIR.defaultBlockState();
        }

        int waterLine = plan.terrainSurfaceY();
        int depth = Math.max(12, Math.min(32, plan.definition().terrain().verticalPadding() / 2));
        int floorY = plan.oceanFloorY() == null
            ? Math.max(outer.minY() + 4, waterLine - depth) : plan.oceanFloorY();
        double distance = TerrainMath.boxDistance2d(basin, outer, x, z);
        if (distance > 0.0) {
            if (!DitheredTerrainFalloff.includesSurfaceVoxel(plan, x, y, z)) {
                return Blocks.AIR.defaultBlockState();
            }
            double taper = TerrainMath.smoothUnit(distance);
            int rimTop = waterLine - (int)Math.round(depth * taper);
            int rimBottom = (int)Math.round(outer.minY() + (rimTop - 4 - outer.minY()) * taper);
            if (y < rimBottom || y > rimTop) return Blocks.AIR.defaultBlockState();
            if (y == rimTop) return palette.surface();
            if (y >= rimTop - 3) return palette.filler();
            return palette.core();
        }

        boolean wall = x == basin.minX() || x == basin.maxX() || z == basin.minZ() || z == basin.maxZ();
        if (wall && y >= floorY && y <= waterLine) {
            return palette.core();
        }
        if (y == floorY) {
            return palette.surface();
        }
        if (y >= floorY - 3 && y < floorY) {
            return palette.filler();
        }
        if (y < floorY) {
            return palette.core();
        }
        if (y <= waterLine) {
            return palette.fluid();
        }
        return Blocks.AIR.defaultBlockState();
    }
}
