package com.cappleapple.instancednotinfinite.structure;

import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;

/**
 * Bounded worldgen writes into an instance's already-generated chunks. Like WorldGenRegion, this
 * writes chunk states without passing generation through gameplay-level block-edit protection.
 * The real LevelChunk still owns heightmaps, lighting, block entities and persistence, and normal
 * level notifications are retained. No gameplay methods or third-party protection settings change.
 */
public final class DungeonGenerationLevel implements WorldGenLevel {
    private final ServerLevel level;
    private final BoundingBox writableBounds;

    public DungeonGenerationLevel(ServerLevel level, BoundingBox writableBounds) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Dungeon generation must run on the server thread");
        this.level = level;
        this.writableBounds = new BoundingBox(writableBounds.minX(), writableBounds.minY(), writableBounds.minZ(),
            writableBounds.maxX(), writableBounds.maxY(), writableBounds.maxZ());
    }

    @Override
    public boolean ensureCanWrite(BlockPos pos) {
        return this.writableBounds.isInside(pos) && !this.level.isOutsideBuildHeight(pos);
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        if (!ensureCanWrite(pos)) return false;
        BlockPos immutable = pos.immutable();
        LevelChunk chunk = this.level.getChunkAt(immutable);
        BlockState previous = chunk.setBlockState(immutable, state, (flags & Block.UPDATE_MOVE_BY_PISTON) != 0);
        if (previous == null) return false;
        this.level.markAndNotifyBlock(immutable, chunk, previous, state, flags, recursionLeft);
        return true;
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean moving) {
        return setBlock(pos, getFluidState(pos).createLegacyBlock(), Block.UPDATE_ALL | (moving ? Block.UPDATE_MOVE_BY_PISTON : 0));
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity entity, int recursionLeft) {
        if (!ensureCanWrite(pos)) return false;
        BlockState state = getBlockState(pos);
        if (state.isAir()) return false;
        if (dropBlock) Block.dropResources(state, this.level, pos, getBlockEntity(pos), entity, ItemStack.EMPTY);
        return setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL, recursionLeft);
    }

    @Override public ServerLevel getLevel() { return this.level; }
    @Override public long getSeed() { return this.level.getSeed(); }
    @Override public RegistryAccess registryAccess() { return this.level.registryAccess(); }
    @Override public FeatureFlagSet enabledFeatures() { return this.level.enabledFeatures(); }
    @Override public BlockState getBlockState(BlockPos pos) { return this.level.getBlockState(pos); }
    @Override public FluidState getFluidState(BlockPos pos) { return this.level.getFluidState(pos); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return this.level.getBlockEntity(pos); }
    @Override public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) { return predicate.test(getBlockState(pos)); }
    @Override public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) { return predicate.test(getFluidState(pos)); }
    @Override public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean required) { return this.level.getChunk(x, z, status, required); }
    @Override public boolean hasChunk(int x, int z) { return this.level.hasChunk(x, z); }
    @Override public ChunkSource getChunkSource() { return this.level.getChunkSource(); }
    @Override public int getHeight(Heightmap.Types type, int x, int z) { return this.level.getHeight(type, x, z); }
    @Override public int getSkyDarken() { return this.level.getSkyDarken(); }
    @Override public BiomeManager getBiomeManager() { return this.level.getBiomeManager(); }
    @Override public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) { return this.level.getUncachedNoiseBiome(x, y, z); }
    @Override public boolean isClientSide() { return false; }
    @Override public int getSeaLevel() { return this.level.getSeaLevel(); }
    @Override public DimensionType dimensionType() { return this.level.dimensionType(); }
    @Override public WorldBorder getWorldBorder() { return this.level.getWorldBorder(); }
    @Override public float getShade(Direction direction, boolean shade) { return this.level.getShade(direction, shade); }
    @Override public LevelLightEngine getLightEngine() { return this.level.getLightEngine(); }
    @Override public LevelData getLevelData() { return this.level.getLevelData(); }
    @Override public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) { return this.level.getCurrentDifficultyAt(pos); }
    @Override public MinecraftServer getServer() { return this.level.getServer(); }
    @Override public RandomSource getRandom() { return this.level.getRandom(); }
    @Override public long nextSubTickCount() { return this.level.nextSubTickCount(); }
    @Override public LevelTickAccess<Block> getBlockTicks() { return this.level.getBlockTicks(); }
    @Override public LevelTickAccess<Fluid> getFluidTicks() { return this.level.getFluidTicks(); }
    @Override public boolean addFreshEntity(Entity entity) { return ensureCanWrite(entity.blockPosition()) && this.level.addFreshEntity(entity); }
    @Override public List<? extends Player> players() { return this.level.players(); }
    @Override public List<Entity> getEntities(@Nullable Entity entity, AABB bounds, Predicate<? super Entity> predicate) {
        return this.level.getEntities(entity, bounds, predicate);
    }
    @Override public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB bounds, Predicate<? super T> predicate) {
        return this.level.getEntities(type, bounds, predicate);
    }
    @Override public void blockUpdated(BlockPos pos, Block block) { this.level.blockUpdated(pos, block); }
    @Override public void playSound(@Nullable Player player, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
        this.level.playSound(player, pos, sound, source, volume, pitch);
    }
    @Override public void addParticle(ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz) {
        this.level.addParticle(particle, x, y, z, dx, dy, dz);
    }
    @Override public void levelEvent(@Nullable Player player, int type, BlockPos pos, int data) { this.level.levelEvent(player, type, pos, data); }
    @Override public void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) { this.level.gameEvent(event, pos, context); }
}
