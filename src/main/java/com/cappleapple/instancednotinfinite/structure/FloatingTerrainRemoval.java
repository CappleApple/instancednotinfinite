package com.cappleapple.instancednotinfinite.structure;

import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Removes temporary flat terrain, preserving all authored writes, including same-state writes. */
public final class FloatingTerrainRemoval {
    private static final ThreadLocal<FloatingTerrainRemoval> CAPTURE = new ThreadLocal<>();
    private final ServerLevel level;
    private final GenerationPlan plan;
    private final DungeonGenerationLevel generation;
    private final LongSet authored = new LongOpenHashSet();

    public FloatingTerrainRemoval(ServerLevel level, GenerationPlan plan) {
        this.level = level;
        this.plan = plan;
        this.generation = new DungeonGenerationLevel(level, plan.envelopeBounds());
    }

    public Capture capture() {
        if (CAPTURE.get() != null) throw new IllegalStateException("Nested floating structure capture");
        CAPTURE.set(this);
        return new Capture();
    }

    public static void record(Level level, BlockPos position) {
        FloatingTerrainRemoval capture = CAPTURE.get();
        if (capture != null && capture.level == level) capture.authored.add(position.asLong());
    }

    public void clearChunk(ChunkPos chunk) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int top = Math.min(this.level.getMaxBuildHeight() - 1, this.plan.terrainSurfaceY());
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                for (int y = top; y >= Math.max(this.level.getMinBuildHeight(), this.plan.envelopeBounds().minY()); y--) {
                    cursor.set(x, y, z);
                    if (!this.plan.envelopeBounds().isInside(cursor) || this.authored.contains(cursor.asLong())) continue;
                    if (!this.level.getBlockState(cursor).isAir()) {
                        this.generation.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
            }
        }
    }

    public void release() {
        this.authored.clear();
    }

    public static final class Capture implements AutoCloseable {
        private Capture() {
        }

        @Override
        public void close() {
            CAPTURE.remove();
        }
    }
}
