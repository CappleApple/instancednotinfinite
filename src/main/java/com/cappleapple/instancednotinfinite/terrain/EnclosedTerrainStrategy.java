package com.cappleapple.instancednotinfinite.terrain;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class EnclosedTerrainStrategy implements TerrainEnvelopeStrategy {
    @Override
    public BlockState blockAt(GenerationPlan plan, MaterialPalette palette, int x, int y, int z) {
        BoundingBox outer = plan.envelopeBounds();
        if (x < outer.minX() || x > outer.maxX() || y < outer.minY() || y > outer.maxY() || z < outer.minZ() || z > outer.maxZ()) {
            return Blocks.AIR.defaultBlockState();
        }
        BoundingBox guaranteed = plan.guaranteedBounds();
        double outerDistance = TerrainMath.boxDistance3d(guaranteed, outer, x, y, z);
        if (outerDistance > 0.0 && !DitheredTerrainFalloff.includesVoxel(plan, x, y, z)) {
            return Blocks.AIR.defaultBlockState();
        }

        // Underground pieces place their authored air into this solid volume. Keeping the
        // pre-placement terrain completely filled prevents subterranean structures from
        // opening into an artificial catch-all cavern.
        return palette.core();
    }
}
