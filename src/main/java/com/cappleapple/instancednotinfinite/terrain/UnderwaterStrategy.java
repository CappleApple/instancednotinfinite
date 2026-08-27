package com.cappleapple.instancednotinfinite.terrain;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class UnderwaterStrategy implements TerrainEnvelopeStrategy {
    private final SurfaceIslandStrategy island = new SurfaceIslandStrategy();
    private final OceanSurfaceStrategy ocean = new OceanSurfaceStrategy();

    @Override
    public BlockState blockAt(GenerationPlan plan, MaterialPalette palette, int x, int y, int z) {
        if (plan.oceanFloorY() != null) return this.ocean.blockAt(plan, palette, x, y, z);
        // Compatibility for existing saved instances, before waterline and seabed were stored separately.
        BlockState terrain = island.blockAt(plan, palette, x, y, z);
        if (!terrain.isAir()) {
            return terrain;
        }
        BoundingBox outer = plan.envelopeBounds();
        BoundingBox guaranteed = plan.guaranteedBounds();
        int waterLine = Math.min(outer.maxY(), plan.structureBounds().maxY() + Math.max(8, plan.definition().terrain().verticalPadding() / 2));
        boolean insideBasin = x >= guaranteed.minX() && x <= guaranteed.maxX()
            && z >= guaranteed.minZ() && z <= guaranteed.maxZ();
        boolean basinWall = x == guaranteed.minX() || x == guaranteed.maxX()
            || z == guaranteed.minZ() || z == guaranteed.maxZ();
        if (insideBasin && basinWall && y <= waterLine && y > plan.structureBounds().minY() - 1) {
            return palette.core();
        }
        if (insideBasin && y <= waterLine && y >= outer.minY()) {
            return palette.fluid();
        }
        return Blocks.AIR.defaultBlockState();
    }
}
