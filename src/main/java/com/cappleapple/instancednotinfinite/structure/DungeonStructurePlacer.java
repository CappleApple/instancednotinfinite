package com.cappleapple.instancednotinfinite.structure;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import com.cappleapple.instancednotinfinite.definition.PlacementEnvironmentInference;
import com.cappleapple.instancednotinfinite.definition.ResolvedDungeonDefinition;
import com.cappleapple.instancednotinfinite.definition.StructureKind;
import com.cappleapple.instancednotinfinite.terrain.DungeonChunkGenerator;
import com.cappleapple.instancednotinfinite.terrain.FoundationSeatingReference;
import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import com.cappleapple.instancednotinfinite.terrain.OceanSurfaceWaterline;
import com.cappleapple.instancednotinfinite.terrain.TerrainSurfaceSeating;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;

public final class DungeonStructurePlacer {
    public PreparedStructure prepare(
        ServerLevel level,
        ResolvedDungeonDefinition definition,
        DungeonChunkGenerator generator,
        long seed,
        boolean inferEnvironment
    ) throws PlacementException {
        EnvironmentType environment = definition.definition().environment();
        boolean aquatic = environment == EnvironmentType.OCEAN_SURFACE || environment == EnvironmentType.UNDERWATER
            || (inferEnvironment && (definition.biome().is(BiomeTags.IS_OCEAN) || definition.biome().is(BiomeTags.IS_RIVER)));
        DungeonChunkGenerator.PlacementSample sample = environment != EnvironmentType.CUSTOM
            && !GenerationPlan.usesUndergroundApproach(environment) ? generator.beginPlacementSampling(aquatic) : null;
        try {
            return switch (definition.structureKind()) {
                case WORLDGEN -> prepareWorldgen(level, definition, generator, seed, inferEnvironment, sample);
                case TEMPLATE -> prepareTemplate(level, definition, generator, sample);
                case AUTO -> throw new PlacementException("AUTO structure kind must be resolved before placement");
            };
        } finally {
            generator.endPlacementSampling();
        }
    }

    public void generateTerrain(ServerLevel level, GenerationPlan plan) {
        // Prime only the chunks needed immediately by the structure and entry. The inexpensive
        // controlled terrain beyond them remains lazy and is bounded by the finite envelope.
        forEachChunk(plan.structureBounds(), (chunkX, chunkZ) -> level.getChunk(chunkX, chunkZ));
        level.getChunkAt(plan.entryPosition());
    }

    public void generateTerrainChunk(ServerLevel level, int chunkX, int chunkZ) {
        level.getChunk(chunkX, chunkZ);
    }

    public void initializePlacement(ServerLevel level, PreparedStructure prepared) {
        if (prepared.worldgenStart() == null) {
            return;
        }
        StructureStart start = prepared.worldgenStart();
        ChunkPos startChunk = start.getChunkPos();
        LevelChunk originChunk = level.getChunk(startChunk.x, startChunk.z);
        level.structureManager().setStartForStructure(
            SectionPos.bottomOf(originChunk), prepared.worldgenStructure(), start, originChunk);
    }

    public void primePlacementHeightmaps(ServerLevel level, int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        Heightmap.primeHeightmaps(
            chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
    }

    /** Places only the part of the prepared structure intersecting one chunk. */
    public void placeChunk(
        ServerLevel level,
        DungeonChunkGenerator generator,
        PreparedStructure prepared,
        long seed,
        int chunkX,
        int chunkZ
    ) throws PlacementException {
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        BoundingBox chunkBounds = new BoundingBox(
            chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ(),
            chunkPos.getMaxBlockX(), level.getMaxBuildHeight() - 1, chunkPos.getMaxBlockZ());
        DungeonGenerationLevel generation = new DungeonGenerationLevel(level, generator.plan().envelopeBounds());
        if (prepared.worldgenStart() != null) {
            LevelChunk chunk = level.getChunk(chunkX, chunkZ);
            primePlacementHeightmaps(level, chunkX, chunkZ);
            level.structureManager().addReferenceForStructure(
                SectionPos.bottomOf(chunk), prepared.worldgenStructure(), prepared.worldgenStart().getChunkPos().toLong(), chunk);
            prepared.worldgenStart().placeInChunk(
                generation, level.structureManager(), generator,
                RandomSource.create(seed ^ chunkPos.toLong()), chunkBounds, chunkPos);
            return;
        }

        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(Rotation.NONE)
            .setIgnoreEntities(false)
            .setFinalizeEntities(true)
            .setKnownShape(false)
            .setBoundingBox(chunkBounds);
        prepared.template().placeInWorld(
            generation, prepared.origin(), prepared.origin(), settings,
            RandomSource.create(seed ^ chunkPos.toLong()), 2);
    }

    public void place(ServerLevel level, DungeonChunkGenerator generator, PreparedStructure prepared, long seed) throws PlacementException {
        initializePlacement(level, prepared);
        forEachChunk(prepared.bounds(), (chunkX, chunkZ) -> primePlacementHeightmaps(level, chunkX, chunkZ));
        forEachChunk(prepared.bounds(), (chunkX, chunkZ) -> {
            try {
                placeChunk(level, generator, prepared, seed, chunkX, chunkZ);
            } catch (PlacementException exception) {
                throw new PlacementRuntimeException(exception);
            }
        });
    }

    private static PreparedStructure prepareWorldgen(
        ServerLevel level,
        ResolvedDungeonDefinition definition,
        DungeonChunkGenerator generator,
        long seed,
        boolean inferEnvironment,
        DungeonChunkGenerator.PlacementSample sample
    ) throws PlacementException {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(definition.structureId());
        if (structure == null) {
            throw new PlacementException("Unknown worldgen structure " + definition.structureId());
        }
        StructureStart start = findCompatibleStart(level, definition, generator, structure, seed);
        boolean usedOceanFloor = generator.sampledOceanFloor();
        ChunkPos startChunk = start.getChunkPos();
        int generatedSurfaceY = generator.getBaseHeight(
            startChunk.getMinBlockX() + 8, startChunk.getMinBlockZ() + 8,
            Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState()) - 1;
        int originalMinimumY = start.getBoundingBox().minY();
        start = fitVerticalEnvelope(level, definition, structure, start);
        BoundingBox bounds = start.getBoundingBox();
        BlockPos origin = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
        int shiftY = bounds.minY() - originalMinimumY;
        int translatedSurfaceY = OceanSurfaceWaterline.translate(generatedSurfaceY, originalMinimumY, bounds.minY());
        BoundingBox pieceBounds = StructurePiece.createBoundingBox(start.getPieces().stream());
        Optional<StructureFoundationAnalyzer.FoundationProfile> foundation = StructureFoundationAnalyzer.profile(level, start);
        EnvironmentType environment = definition.definition().environment();
        PlacementEvidence evidence = inferEnvironment && sample != null
            ? new PlacementEvidence(environment, translatedSurfaceY,
                sample.oceanFloorY() == null ? null : sample.oceanFloorY() + shiftY, usedOceanFloor) : null;
        if (inferEnvironment && sample != null) {
            int groundY = foundation.map(StructureFoundationAnalyzer.FoundationProfile::placementGroundY).orElse(pieceBounds.minY());
            EnvironmentType inferred = PlacementEnvironmentInference.classify(environment,
                pieceBounds.minY() - shiftY, pieceBounds.maxY() - shiftY, groundY - shiftY,
                sample.surfaceY(), sample.oceanFloorY(), usedOceanFloor);
            InstancedNotInfinite.LOGGER.info(
                "Placement evidence for {}: environment {} -> {}, pieces Y={}..{}, ground Y={}, flat surface Y={}, seabed Y={}, ocean-floor query={}",
                definition.structureId(), environment, inferred, pieceBounds.minY() - shiftY, pieceBounds.maxY() - shiftY,
                groundY - shiftY, sample.surfaceY(), sample.oceanFloorY(), usedOceanFloor);
            environment = inferred;
            definition = new ResolvedDungeonDefinition(definition.definition().withEnvironment(environment),
                definition.structureId(), definition.structureKind(), definition.biome(), definition.biomeId());
        }
        Integer oceanFloorY = sample != null && sample.oceanFloorY() != null
            && (environment == EnvironmentType.UNDERWATER || environment == EnvironmentType.OCEAN_SURFACE)
            ? sample.oceanFloorY() + shiftY : null;
        if (environment == EnvironmentType.FLOATING_ISLAND) translatedSurfaceY = Math.min(translatedSurfaceY, pieceBounds.minY() - 1);
        boolean adaptsTerrain = structure.terrainAdaptation() != TerrainAdjustment.NONE;
        int seatingReferenceY = foundation
            .map(profile -> FoundationSeatingReference.select(
                profile.foundation(), profile.placementGroundY(), adaptsTerrain))
            .orElse(pieceBounds.minY());
        int seatedSurfaceY = GenerationPlan.seatGroundedSurface(
            definition.definition().environment(), bounds.minY(), bounds.maxY(), seatingReferenceY, translatedSurfaceY);
        if (sample != null && foundation.isPresent()) {
            seatedSurfaceY = TerrainSurfaceSeating.seatToFoundation(
                definition.definition().environment(), seatingReferenceY, translatedSurfaceY);
        }
        if (seatedSurfaceY != translatedSurfaceY) {
            String foundationDescription = foundation
                .map(profile -> profile.foundation().baseY() + ".." + profile.foundation().topY()
                    + ", placement ground Y=" + profile.placementGroundY())
                .orElse("unavailable");
            InstancedNotInfinite.LOGGER.info(
                "Seated dungeon {} terrain at Y={} using terrain adaptation {} and authored foundation {} "
                    + "(piece bounds Y={}..{}, adjusted bounds Y={}..{}, sampled surface Y={})",
                definition.structureId(), seatedSurfaceY, structure.terrainAdaptation(), foundationDescription,
                pieceBounds.minY(), pieceBounds.maxY(), bounds.minY(), bounds.maxY(), translatedSurfaceY);
        }
        List<BoundingBox> authoredPieceBounds = start.getPieces().stream()
            .map(StructurePiece::getBoundingBox)
            .toList();
        return new PreparedStructure(
            definition, bounds, authoredPieceBounds, origin, seatedSurfaceY, oceanFloorY, start, structure, null, evidence);
    }

    private static StructureStart fitVerticalEnvelope(
        ServerLevel level,
        ResolvedDungeonDefinition definition,
        Structure structure,
        StructureStart start
    ) throws PlacementException {
        BoundingBox bounds = start.getBoundingBox();
        int minimumStructureY = minimumStructureY(level, definition);
        int maximumStructureY = maximumStructureY(level, definition);
        if (bounds.getYSpan() > maximumStructureY - minimumStructureY + 1) {
            throw new PlacementException(
                "Worldgen structure " + definition.structureId() + " is " + bounds.getYSpan()
                    + " blocks tall and cannot fit its vertical padding and falloff in the instance build range");
        }

        int shiftY = 0;
        if (bounds.minY() < minimumStructureY) {
            shiftY = minimumStructureY - bounds.minY();
        }
        if (bounds.maxY() + shiftY > maximumStructureY) {
            shiftY += maximumStructureY - (bounds.maxY() + shiftY);
        }
        if (shiftY == 0) {
            return start;
        }

        for (StructurePiece piece : start.getPieces()) {
            piece.move(0, shiftY, 0);
        }
        StructureStart shifted = new StructureStart(
            structure, start.getChunkPos(), start.getReferences(), new PiecesContainer(start.getPieces()));
        BoundingBox shiftedBounds = shifted.getBoundingBox();
        if (shiftedBounds.minY() < minimumStructureY || shiftedBounds.maxY() > maximumStructureY) {
            throw new PlacementException(
                "Worldgen structure " + definition.structureId() + " could not be translated into the safe vertical terrain range");
        }
        return shifted;
    }

    private static StructureStart findCompatibleStart(
        ServerLevel level,
        ResolvedDungeonDefinition definition,
        DungeonChunkGenerator generator,
        Structure structure,
        long seed
    ) throws PlacementException {
        int horizontalRoom = definition.definition().terrain().maximumRadius()
            - definition.definition().terrain().horizontalPadding() - 16;
        int searchRadius = Math.max(0, Math.min(8, horizontalRoom / 16));
        int attempts = 0;
        for (int radius = 0; radius <= searchRadius; radius++) {
            for (int chunkX = -radius; chunkX <= radius; chunkX++) {
                for (int chunkZ = -radius; chunkZ <= radius; chunkZ++) {
                    if (Math.max(Math.abs(chunkX), Math.abs(chunkZ)) != radius) continue;
                    attempts++;
                    ChunkPos candidate = new ChunkPos(chunkX, chunkZ);
                    StructureStart start;
                    try (var ignored = ControlledStructureStartGeneration.begin()) {
                        start = structure.generate(
                            level.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
                            level.getStructureManager(), seed, candidate, 0, level, holder -> true);
                    }
                    if (!start.isValid()) continue;
                    if (attempts > 1) {
                        InstancedNotInfinite.LOGGER.info(
                            "Found compatible start for {} at chunk {},{} after {} attempts",
                            definition.structureId(), chunkX, chunkZ, attempts);
                    }
                    return start;
                }
            }
        }
        throw new PlacementException(
            "Worldgen structure " + definition.structureId() + " could not produce a compatible start in " + attempts
                + " candidate chunks inside the dungeon radius");
    }

    private static int minimumStructureY(ServerLevel level, ResolvedDungeonDefinition definition) {
        if (definition.definition().environment()
            != com.cappleapple.instancednotinfinite.definition.EnvironmentType.CUSTOM) {
            return Math.max(level.getMinBuildHeight(), GenerationPlan.MIN_TERRAIN_Y);
        }
        int padding = definition.definition().terrain().verticalPadding();
        return Math.max(level.getMinBuildHeight() + 1, GenerationPlan.MIN_TERRAIN_Y)
            + padding + GenerationPlan.verticalFalloffForPadding(padding);
    }

    private static int maximumStructureY(ServerLevel level, ResolvedDungeonDefinition definition) {
        if (definition.definition().environment()
            != com.cappleapple.instancednotinfinite.definition.EnvironmentType.CUSTOM) {
            return Math.min(level.getMaxBuildHeight() - 1, GenerationPlan.MAX_TERRAIN_Y);
        }
        int padding = definition.definition().terrain().verticalPadding();
        return Math.min(level.getMaxBuildHeight() - 1, GenerationPlan.MAX_TERRAIN_Y)
            - padding - GenerationPlan.verticalFalloffForPadding(padding);
    }

    private static PreparedStructure prepareTemplate(
        ServerLevel level,
        ResolvedDungeonDefinition definition,
        DungeonChunkGenerator generator,
        DungeonChunkGenerator.PlacementSample sample
    ) throws PlacementException {
        Optional<StructureTemplate> optional = level.getStructureManager().get(definition.structureId());
        StructureTemplate template = optional.orElseThrow(
            () -> new PlacementException("Unknown structure template " + definition.structureId()));
        Vec3i size = template.getSize();
        if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1) {
            throw new PlacementException("Structure template " + definition.structureId() + " is empty");
        }
        int padding = definition.definition().terrain().verticalPadding();
        int falloff = GenerationPlan.verticalFalloffForPadding(padding);
        int minimumY = GenerationPlan.MIN_TERRAIN_Y + padding + falloff;
        int maximumY = GenerationPlan.MAX_TERRAIN_Y - padding - falloff - size.getY() + 1;
        Heightmap.Types heightmap = definition.definition().environment() == com.cappleapple.instancednotinfinite.definition.EnvironmentType.UNDERWATER
            ? Heightmap.Types.OCEAN_FLOOR_WG
            : Heightmap.Types.WORLD_SURFACE_WG;
        int naturalSurfaceY = generator.getBaseHeight(
            0, 0, heightmap, level, level.getChunkSource().randomState()) - 1;
        int desiredY = GenerationPlan.usesSurfaceApproach(definition.definition().environment())
            || definition.definition().environment() == com.cappleapple.instancednotinfinite.definition.EnvironmentType.UNDERWATER
                ? naturalSurfaceY + 1
                : definition.definition().height().midpoint();
        int originY = Math.max(minimumY, Math.min(maximumY, desiredY));
        BlockPos origin = new BlockPos(-size.getX() / 2, originY, -size.getZ() / 2);
        StructurePlaceSettings settings = new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(Rotation.NONE);
        BoundingBox bounds = template.getBoundingBox(settings, origin);
        int terrainSurfaceY = sample != null && sample.oceanFloorY() != null ? sample.surfaceY()
            : GenerationPlan.usesSurfaceApproach(definition.definition().environment())
            ? naturalSurfaceY
            : origin.getY() - 1;
        return new PreparedStructure(definition, bounds, List.of(bounds), origin, terrainSurfaceY,
            sample == null ? null : sample.oceanFloorY(), null, null, template, null);
    }

    private static void forEachChunk(BoundingBox bounds, ChunkConsumer consumer) {
        int minChunkX = SectionPos.blockToSectionCoord(bounds.minX());
        int maxChunkX = SectionPos.blockToSectionCoord(bounds.maxX());
        int minChunkZ = SectionPos.blockToSectionCoord(bounds.minZ());
        int maxChunkZ = SectionPos.blockToSectionCoord(bounds.maxZ());
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                consumer.accept(chunkX, chunkZ);
            }
        }
    }

    public record PreparedStructure(
        ResolvedDungeonDefinition definition,
        BoundingBox bounds,
        List<BoundingBox> authoredPieceBounds,
        BlockPos origin,
        int terrainSurfaceY,
        Integer oceanFloorY,
        StructureStart worldgenStart,
        Structure worldgenStructure,
        StructureTemplate template,
        PlacementEvidence environmentEvidence
    ) {
        public PreparedStructure {
            authoredPieceBounds = List.copyOf(authoredPieceBounds);
        }
    }

    public record PlacementEvidence(EnvironmentType hint, int surfaceY, Integer oceanFloorY, boolean usedOceanFloor) {
        public EnvironmentType classify(BoundingBox actualPieces) {
            return PlacementEnvironmentInference.classify(hint, actualPieces.minY(), actualPieces.maxY(),
                actualPieces.minY(), surfaceY, oceanFloorY, usedOceanFloor);
        }
    }

    @FunctionalInterface
    private interface ChunkConsumer {
        void accept(int chunkX, int chunkZ);
    }

    private static final class PlacementRuntimeException extends RuntimeException {
        private PlacementRuntimeException(PlacementException cause) {
            super(cause);
        }
    }
}
