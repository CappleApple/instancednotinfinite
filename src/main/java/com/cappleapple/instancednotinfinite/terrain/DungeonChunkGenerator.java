package com.cappleapple.instancednotinfinite.terrain;

import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import com.mojang.serialization.MapCodec;
import java.util.List;
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
    private final MaterialPalette palette;
    private final TerrainEnvelopeStrategy terrainStrategy;
    private final boolean customTerrain;

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
        this.plan.set(updated);
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
                    BlockState state = this.terrainStrategy.blockAt(current, this.palette, x, y, z);
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
        GenerationPlan current = this.plan.get();
        for (int y = Math.min(level.getMaxBuildHeight() - 1, current.envelopeBounds().maxY()); y >= level.getMinBuildHeight(); y--) {
            BlockState state = this.terrainStrategy.blockAt(current, this.palette, x, y, z);
            if (type.isOpaque().test(state)) return y + 1;
        }
        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] states = new BlockState[level.getHeight()];
        GenerationPlan current = this.plan.get();
        for (int index = 0; index < states.length; index++) {
            int y = level.getMinBuildHeight() + index;
            states[index] = this.terrainStrategy.blockAt(current, this.palette, x, y, z);
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
