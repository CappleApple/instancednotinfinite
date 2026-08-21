package com.cappleapple.instancednotinfinite.snapshot;

import com.cappleapple.instancednotinfinite.instance.DungeonInstance;
import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Extracts the authored structure shell after each real structure chunk is placed. */
public final class DungeonVisualSnapshotBuilder {
    private final DungeonInstance instance;
    private final GenerationPlan plan;
    private final int maximumBlocks;
    private final boolean retainSnapshot;
    private final Map<Long, VisualBlock> blocks = new LinkedHashMap<>();
    private BlockState[] beforeStructure;
    private ChunkPos beforeChunk;
    private final Set<Long> structurePositions = new HashSet<>();

    public DungeonVisualSnapshotBuilder(
        DungeonInstance instance,
        GenerationPlan plan,
        int maximumBlocks,
        boolean retainSnapshot
    ) {
        this.instance = instance;
        this.plan = plan;
        this.maximumBlocks = maximumBlocks;
        this.retainSnapshot = retainSnapshot;
    }

    /** Records controlled terrain immediately before the selected structure modifies one chunk. */
    public void beginStructureChunk(ServerLevel level, ChunkPos chunk) {
        this.beforeChunk = chunk;
        this.beforeStructure = new BlockState[level.getHeight() * 16 * 16];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                    cursor.set(x, y, z);
                    this.beforeStructure[index(level, chunk, x, y, z)] = level.getBlockState(cursor);
                }
            }
        }
    }

    public List<VisualBlock> captureChunk(ServerLevel level, ChunkPos chunk, boolean structurePass) {
        if (!structurePass) return List.of();
        BoundingBox structure = this.plan.structureBounds();
        boolean structureChunk = chunk.getMaxBlockX() >= structure.minX() && chunk.getMinBlockX() <= structure.maxX()
            && chunk.getMaxBlockZ() >= structure.minZ() && chunk.getMinBlockZ() <= structure.maxZ();
        if (!structureChunk) return List.of();
        if (this.beforeStructure == null || !chunk.equals(this.beforeChunk)) return List.of();
        List<VisualBlock> added = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        captureStructureChanges(
            level, chunk.getMinBlockX(), chunk.getMaxBlockX(), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1,
            chunk.getMinBlockZ(), chunk.getMaxBlockZ(), cursor, added);
        this.beforeStructure = null;
        this.beforeChunk = null;
        return List.copyOf(added);
    }

    private void captureStructureChanges(
        ServerLevel level,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        BlockPos.MutableBlockPos cursor,
        List<VisualBlock> added
    ) {
        BoundingBox envelope = this.plan.envelopeBounds();
        List<BlockPos> changedBlocks = this.retainSnapshot ? new ArrayList<>() : List.of();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    BlockState before = this.beforeStructure[index(level, this.beforeChunk, x, y, z)];
                    if (state.equals(before)) continue;
                    if (state.isAir()) {
                        this.structurePositions.remove(cursor.asLong());
                        continue;
                    }
                    this.structurePositions.add(cursor.asLong());
                    if (this.retainSnapshot) changedBlocks.add(cursor.immutable());
                }
            }
        }
        for (BlockPos changed : changedBlocks) {
            if (!isStructureSurface(level, changed)) continue;
            BlockPos local = new BlockPos(
                changed.getX() - envelope.minX(), changed.getY() - envelope.minY(), changed.getZ() - envelope.minZ());
            put(new VisualBlock(local, level.getBlockState(changed), VisualLayer.STRUCTURE), this.maximumBlocks, added);
        }
    }

    private boolean isStructureSurface(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            BlockState state = level.getBlockState(neighbor);
            if (state.isAir() || !this.structurePositions.contains(neighbor.asLong())) {
                return true;
            }
        }
        return false;
    }

    private void put(VisualBlock visual, int limit, List<VisualBlock> added) {
        long key = visual.position().asLong();
        VisualBlock existing = this.blocks.get(key);
        if (existing == null && this.blocks.size() >= limit) return;
        VisualBlock previous = this.blocks.put(key, visual);
        if (!visual.equals(previous)) added.add(visual);
    }

    public DungeonVisualSnapshot build() {
        BoundingBox box = this.plan.envelopeBounds();
        BoundingBox normalized = new BoundingBox(0, 0, 0, box.getXSpan() - 1, box.getYSpan() - 1, box.getZSpan() - 1);
        return new DungeonVisualSnapshot(
            ResourceLocation.parse(this.instance.definition().id()), this.instance.biomeId(), this.instance.seed(), normalized,
            List.copyOf(this.blocks.values()));
    }

    public int retainedBlockCount() {
        return this.blocks.size();
    }

    private static int index(ServerLevel level, ChunkPos chunk, int x, int y, int z) {
        int localX = x - chunk.getMinBlockX();
        int localZ = z - chunk.getMinBlockZ();
        return (y - level.getMinBuildHeight()) * 256 + localZ * 16 + localX;
    }

}
