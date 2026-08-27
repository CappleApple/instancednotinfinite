package com.cappleapple.instancednotinfinite.terrain;

import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Cheap deterministic finite terrain with one fixed selected biome and explicit structure placement.
 * It retains NoiseBasedChunkGenerator's public structure-generation contract but deliberately skips
 * vanilla density, surface, carver, and feature passes.
 */
public final class DungeonChunkGenerator extends NoiseBasedChunkGenerator {
    private final AtomicReference<GenerationPlan> plan;
    private final Holder<Biome> biome;
    private volatile MaterialPalette palette;
    private volatile TerrainEnvelopeStrategy terrainStrategy;
    private final boolean customTerrain;
    private volatile PlacementSample placementSample;
    private boolean sampledOceanFloor;
    private volatile boolean temporaryFloatingTerrain;
    private final Set<ChunkPos> temporaryChunks = ConcurrentHashMap.newKeySet();

    public DungeonChunkGenerator(
        Holder<Biome> biome,
        Holder<NoiseGeneratorSettings> noiseSettings,
        GenerationPlan initialPlan
    ) {
        super(new FixedBiomeSource(biome), noiseSettings);
        this.plan = new AtomicReference<>(initialPlan);
        this.biome = biome;
        this.palette = MaterialPalette.forDefinition(initialPlan.definition(), biome);
        this.customTerrain = initialPlan.definition().environment() == EnvironmentType.CUSTOM;
        if (this.customTerrain) {
            ResourceLocation customId = ResourceLocation.tryParse(initialPlan.definition().customEnvironment());
            if (customId == null) {
                throw new IllegalArgumentException("Invalid custom terrain strategy id " + initialPlan.definition().customEnvironment());
            }
            this.terrainStrategy = CustomTerrainStrategies.require(customId);
        } else {
            this.terrainStrategy = TerrainStrategyRegistry.forEnvironment(initialPlan.definition().environment());
        }
    }

    public GenerationPlan plan() {
        return this.plan.get();
    }

    public void updatePlan(GenerationPlan updated) {
        this.palette = MaterialPalette.forDefinition(updated.definition(), this.biome);
        if (!this.customTerrain) this.terrainStrategy = TerrainStrategyRegistry.forEnvironment(updated.definition().environment());
        this.plan.set(updated);
    }

    public PlacementSample beginPlacementSampling(boolean aquatic) {
        int surface = aquatic ? getSeaLevel() : Math.max(63, getSeaLevel());
        Integer floor = aquatic ? surface - 24 : null;
        this.sampledOceanFloor = false;
        return this.placementSample = new PlacementSample(surface, floor);
    }

    public boolean sampledOceanFloor() {
        return this.sampledOceanFloor;
    }

    public void endPlacementSampling() {
        this.placementSample = null;
    }

    public void beginFloatingTerrain() {
        this.temporaryFloatingTerrain = true;
    }

    public List<ChunkPos> finishFloatingTerrain() {
        this.temporaryFloatingTerrain = false;
        List<ChunkPos> result = this.temporaryChunks.stream()
            .sorted(java.util.Comparator.comparingLong(ChunkPos::toLong)).toList();
        this.temporaryChunks.clear();
        return result;
    }

    private BlockState terrainBlock(GenerationPlan current, int x, int y, int z) {
        if (current.floatingVoid()) {
            if (!this.temporaryFloatingTerrain || y > current.terrainSurfaceY()
                || !current.envelopeBounds().isInside(x, y, z)) return Blocks.AIR.defaultBlockState();
            return flatBlock(y, current.terrainSurfaceY(), null);
        }
        return this.terrainStrategy.blockAt(current, this.palette, x, y, z);
    }

    private BlockState flatBlock(int y, int surface, Integer floor) {
        int solidSurface = floor == null ? surface : floor;
        if (y > surface) return Blocks.AIR.defaultBlockState();
        if (y > solidSurface) return Blocks.WATER.defaultBlockState();
        if (y == solidSurface) return this.palette.surface();
        if (y >= solidSurface - 3) return this.palette.filler();
        return this.palette.core();
    }

    public record PlacementSample(int surfaceY, Integer oceanFloorY) {
    }

    public boolean usesCustomTerrain() {
        return this.customTerrain;
    }

    public boolean usesSyntheticTerrain() {
        return true;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        // Runtime levels are reconstructed from persistent instance records, never from a serialized LevelStem.
        return MapCodec.unit(this);
    }

    @Override
    public void createStructures(
        RegistryAccess registryAccess,
        ChunkGeneratorStructureState structureState,
        StructureManager structureManager,
        ChunkAccess chunk,
        StructureTemplateManager structureTemplateManager
    ) {
        // Normal structure sets are suppressed; DungeonStructurePlacer registers and places only the selected start.
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
        Blender blender,
        RandomState randomState,
        StructureManager structures,
        ChunkAccess chunk
    ) {
        GenerationPlan current = this.plan.get();
        int chunkMinX = chunk.getPos().getMinBlockX();
        int chunkMinZ = chunk.getPos().getMinBlockZ();
        if (chunkMinX > current.envelopeBounds().maxX() || chunkMinX + 15 < current.envelopeBounds().minX()
            || chunkMinZ > current.envelopeBounds().maxZ() || chunkMinZ + 15 < current.envelopeBounds().minZ()) {
            return CompletableFuture.completedFuture(chunk);
        }
        if (current.floatingVoid() && this.temporaryFloatingTerrain) this.temporaryChunks.add(chunk.getPos());

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int minY = Math.max(chunk.getMinBuildHeight(), current.envelopeBounds().minY());
        int maxY = Math.min(chunk.getMaxBuildHeight() - 1, current.envelopeBounds().maxY());
        for (int localX = 0; localX < 16; localX++) {
            int x = chunkMinX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = chunkMinZ + localZ;
                for (int y = minY; y <= maxY; y++) {
                    BlockState state = terrainBlock(current, x, y, z);
                    if (!state.isAir()) {
                        chunk.setBlockState(mutable.set(localX, y, localZ), state, false);
                        ocean.update(localX, y, localZ, state);
                        surface.update(localX, y, localZ, state);
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        PlacementSample sample = this.placementSample;
        if (sample != null) {
            if (type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG) this.sampledOceanFloor = true;
            int top = sample.surfaceY();
            if (sample.oceanFloorY() != null && !type.isOpaque().test(Blocks.WATER.defaultBlockState())) {
                top = sample.oceanFloorY();
            }
            return Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight(), top + 1));
        }
        GenerationPlan current = this.plan.get();
        for (int y = Math.min(level.getMaxBuildHeight() - 1, current.envelopeBounds().maxY()); y >= level.getMinBuildHeight(); y--) {
            BlockState state = terrainBlock(current, x, y, z);
            if (type.isOpaque().test(state)) return y + 1;
        }
        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] states = new BlockState[level.getHeight()];
        GenerationPlan current = this.plan.get();
        PlacementSample sample = this.placementSample;
        for (int index = 0; index < states.length; index++) {
            int y = level.getMinBuildHeight() + index;
            states[index] = sample == null ? terrainBlock(current, x, y, z)
                : flatBlock(y, sample.surfaceY(), sample.oceanFloorY());
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState randomState, ChunkAccess chunk) {
        // The finite strategy writes its final surface directly during fillFromNoise.
    }

    @Override
    public void applyCarvers(
        WorldGenRegion region,
        long seed,
        RandomState randomState,
        BiomeManager biomes,
        StructureManager structures,
        ChunkAccess chunk,
        GenerationStep.Carving carving
    ) {
        // Controlled terrain intentionally has no vanilla carver pass.
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        GenerationPlan current = this.plan.get();
        if (current.definition().decoration() == com.cappleapple.instancednotinfinite.definition.DecorationMode.NONE
            || current.floatingVoid()
            || current.definition().environment() == EnvironmentType.CAVE
            || current.definition().environment() == EnvironmentType.UNDERGROUND) {
            return;
        }
        if (current.definition().decoration() == com.cappleapple.instancednotinfinite.definition.DecorationMode.FULL) {
            super.applyBiomeDecoration(level, chunk, structures);
            return;
        }

        // SAFE decoration intentionally runs only the two inexpensive surface-oriented stages.
        // This retains trees, plants, mushrooms, kelp, snow, and ice from the selected biome
        // without invoking ores, carvers, lakes, or the rest of vanilla terrain generation.
        List<HolderSet<PlacedFeature>> features = this.biome.value().getGenerationSettings().features();
        SectionPos section = SectionPos.of(chunk.getPos(), level.getMinSection());
        BlockPos origin = section.origin();
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(level.getSeed(), origin.getX(), origin.getZ());
        placeFeatureStep(level, features, GenerationStep.Decoration.VEGETAL_DECORATION, random, decorationSeed, origin);
        placeFeatureStep(level, features, GenerationStep.Decoration.TOP_LAYER_MODIFICATION, random, decorationSeed, origin);
    }

    private void placeFeatureStep(
        WorldGenLevel level,
        List<HolderSet<PlacedFeature>> features,
        GenerationStep.Decoration step,
        WorldgenRandom random,
        long decorationSeed,
        BlockPos origin
    ) {
        int stepIndex = step.ordinal();
        if (stepIndex >= features.size()) {
            return;
        }
        int featureIndex = 0;
        for (Holder<PlacedFeature> feature : features.get(stepIndex)) {
            random.setFeatureSeed(decorationSeed, featureIndex++, stepIndex);
            feature.value().placeWithBiomeCheck(level, this, random, origin);
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // Runtime spawning is handled normally; the expensive worldgen creature pass is omitted.
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return Math.min(level.getMaxBuildHeight() - 1, this.plan.get().terrainSurfaceY() + 2);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
        lines.add(this.customTerrain
            ? "Instanced dungeon custom terrain envelope"
            : "Instanced dungeon dithered finite terrain");
    }
}
