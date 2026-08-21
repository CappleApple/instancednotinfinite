package com.cappleapple.instancednotinfinite.terrain;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Finite surface terrain anchored to the bottom of the instance build range. */
public final class GroundedSurfaceStrategy implements TerrainEnvelopeStrategy {
    @Override
    public BlockState blockAt(GenerationPlan plan, MaterialPalette palette, int x, int y, int z) {
        BoundingBox outer = plan.envelopeBounds();
        if (x < outer.minX() || x > outer.maxX() || y < outer.minY() || y > outer.maxY()
            || z < outer.minZ() || z > outer.maxZ()) {
            return Blocks.AIR.defaultBlockState();
        }

        BoundingBox guaranteed = plan.guaranteedBounds();
        double distance = TerrainMath.boxDistance2d(guaranteed, outer, x, z);
        if (!DitheredTerrainFalloff.includesSurfaceVoxel(plan, x, y, z)) {
            return Blocks.AIR.defaultBlockState();
        }

        BoundingBox structure = plan.structureBounds();
        boolean underStructure = x >= structure.minX() - 2 && x <= structure.maxX() + 2
            && z >= structure.minZ() - 2 && z <= structure.maxZ() + 2;
        int surfaceY = plan.terrainSurfaceY();
        if (!underStructure) {
            surfaceY += TerrainMath.surfaceVariation(plan.seed(), x, z);
        }
        if (distance > 0.0) {
            surfaceY -= (int)Math.round(12.0 * Math.min(1.0, distance));
        }
        int bottomY = outer.minY();
        if (distance > 0.0) {
            double taper = TerrainMath.smoothUnit(distance);
            bottomY = (int)Math.round(bottomY + (surfaceY - 4 - bottomY) * taper);
        }
        if (y < bottomY || y > surfaceY) {
            return Blocks.AIR.defaultBlockState();
        }
        if (y == surfaceY) {
            return palette.surface();
        }
        if (y >= surfaceY - 3) {
            return palette.filler();
        }
        return palette.core();
    }
}
