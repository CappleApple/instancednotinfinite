package com.cappleapple.instancednotinfinite.terrain;

import com.cappleapple.instancednotinfinite.definition.DecorationMode;
import com.cappleapple.instancednotinfinite.definition.DungeonDefinition;
import com.cappleapple.instancednotinfinite.definition.StructureKind;
import java.util.function.IntFunction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Ancient City templates expect an existing cave; their structure void does not excavate rock. */
public final class AncientCityTerrainStrategy implements TerrainEnvelopeStrategy {
    public static final int MINIMUM_HORIZONTAL_PADDING = 32;
    public static final int MINIMUM_VERTICAL_PADDING = 24;
    private static final int SHELL_THICKNESS = 5;
    private static final TerrainEnvelopeStrategy ENCLOSED = new EnclosedTerrainStrategy();
    private static final MaterialPalette DEEPSLATE = new MaterialPalette(Blocks.DEEPSLATE.defaultBlockState(),
        Blocks.DEEPSLATE.defaultBlockState(), Blocks.SCULK.defaultBlockState(), Blocks.AIR.defaultBlockState());

    public static boolean appliesTo(DungeonDefinition definition) {
        return definition.structure().equals("minecraft:ancient_city")
            && definition.structureKind() != StructureKind.TEMPLATE
            && GenerationPlan.usesUndergroundApproach(definition.environment());
    }

    @Override
    public BlockState blockAt(GenerationPlan plan, MaterialPalette palette, int x, int y, int z) {
        return column(plan, palette, x, z).apply(y);
    }

    @Override
    public IntFunction<BlockState> column(GenerationPlan plan, MaterialPalette palette, int x, int z) {
        BoundingBox city = plan.structureBounds();
        BoundingBox shell = plan.guaranteedBounds();
        int dx = Math.max(0, Math.max(city.minX() - x, x - city.maxX()));
        int dz = Math.max(0, Math.max(city.minZ() - z, z - city.maxZ()));
        double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
        // Smooth, rounded margins stay inside the guaranteed solid shell, before its outer falloff.
        double margin = 22.0 + 4.0 * DeterministicNoise.smooth2d(plan.seed() ^ 0xA11CE, x, z, 32);
        int floor = AncientCityFloor.height(plan.seed(), plan.terrainSurfaceY(), x, z, distance);
        int ceiling = city.maxY() + 10 + (int) Math.floor(4.0
            * DeterministicNoise.smooth2d(plan.seed() ^ 0xCE11, x, z, 32));
        double taper = Math.clamp((distance - 6.0) / (margin - 6.0), 0.0, 1.0);
        double center = (floor + ceiling) * 0.5;
        double halfHeight = (ceiling - floor) * 0.5 * Math.sqrt(1.0 - taper * taper);
        int bottom = Math.max(shell.minY() + SHELL_THICKNESS, (int) Math.floor(center - halfHeight));
        int top = Math.min(shell.maxY() - SHELL_THICKNESS, (int) Math.ceil(center + halfHeight));
        boolean hollow = distance < margin
            && x >= shell.minX() + SHELL_THICKNESS && x <= shell.maxX() - SHELL_THICKNESS
            && z >= shell.minZ() + SHELL_THICKNESS && z <= shell.maxZ() - SHELL_THICKNESS
            && top - bottom >= 2;
        boolean sculk = plan.definition().decoration() != DecorationMode.NONE
            && DeterministicNoise.smooth2d(plan.seed() ^ 0x5C01C, x, z, 12) > -0.15;
        return y -> {
            if (hollow && y > bottom && y < top) return Blocks.CAVE_AIR.defaultBlockState();
            if (hollow && y == bottom && sculk) return Blocks.SCULK.defaultBlockState();
            return ENCLOSED.blockAt(plan, DEEPSLATE, x, y, z);
        };
    }
}
