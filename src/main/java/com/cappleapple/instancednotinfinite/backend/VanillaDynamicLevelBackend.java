package com.cappleapple.instancednotinfinite.backend;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import com.cappleapple.instancednotinfinite.definition.ResolvedDungeonDefinition;
import com.cappleapple.instancednotinfinite.instance.InstanceId;
import com.cappleapple.instancednotinfinite.mixin.MinecraftServerAccessor;
import com.cappleapple.instancednotinfinite.terrain.DungeonChunkGenerator;
import com.cappleapple.instancednotinfinite.terrain.GenerationPlan;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

public final class VanillaDynamicLevelBackend implements DynamicLevelBackend {
    public static final String MARKER_FILE = ".instancednotinfinite-instance";

    @Override
    public CreatedLevel create(MinecraftServer server, InstanceId id, ResolvedDungeonDefinition definition, long seed) throws Exception {
        requireServerThread(server);
        ResourceKey<Level> levelKey = levelKey(id);
        Map<ResourceKey<Level>, ServerLevel> levels = levels(server);
        if (levels.containsKey(levelKey)) {
            throw new IllegalStateException("Runtime level already exists: " + levelKey.location());
        }

        GenerationPlan initialPlan = GenerationPlan.fallback(seed, definition.definition());
        EnvironmentType dimensionEnvironment = definition.definition().environment();
        if (dimensionEnvironment == EnvironmentType.FLOATING_ISLAND) {
            if (definition.biome().is(net.minecraft.tags.BiomeTags.IS_NETHER)) dimensionEnvironment = EnvironmentType.NETHER_LIKE;
            else if (definition.biome().is(net.minecraft.tags.BiomeTags.IS_END)) dimensionEnvironment = EnvironmentType.END_LIKE;
        }
        Holder<NoiseGeneratorSettings> noiseSettings = noiseSettings(server, dimensionEnvironment);
        DungeonChunkGenerator generator = new DungeonChunkGenerator(definition.biome(), noiseSettings, initialPlan);
        Holder<DimensionType> dimensionType = dimensionType(server, dimensionEnvironment);
        LevelStem stem = new LevelStem(dimensionType, generator);
        LevelStorageSource.LevelStorageAccess storage = storage(server);
        DerivedLevelData levelData = new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData());
        ServerLevel level = new ServerLevel(
            server,
            Util.backgroundExecutor(),
            storage,
            levelData,
            levelKey,
            stem,
            NoOpChunkProgressListener.INSTANCE,
            false,
            seed,
            List.of(),
            true,
            server.overworld().getRandomSequences());
        level.getWorldBorder().setCenter(0.0, 0.0);
        level.getWorldBorder().setSize(definition.definition().terrain().maximumRadius() * 2.0);

        Path path = storage.getDimensionPath(levelKey).toAbsolutePath().normalize();
        boolean registered = false;
        try {
            Files.createDirectories(path);
            Files.writeString(
                path.resolve(MARKER_FILE),
                "mod=" + InstancedNotInfinite.MOD_ID + "\ninstance=" + id + "\nschema=1\n",
                StandardCharsets.UTF_8);
            levels.put(levelKey, level);
            server.markWorldsDirty();
            registered = true;
            NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
            InstancedNotInfinite.LOGGER.info("[Dungeon {}] Runtime level {} registered", id.shortId(), levelKey.location());
            return new CreatedLevel(level, generator, path);
        } catch (Exception exception) {
            if (registered && levels.remove(levelKey, level)) {
                server.markWorldsDirty();
            }
            try {
                level.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    @Override
    public void unload(MinecraftServer server, ResourceKey<Level> key) throws IOException {
        requireServerThread(server);
        ServerLevel level = levels(server).get(key);
        if (level == null) {
            return;
        }
        if (!level.players().isEmpty()) {
            throw new IllegalStateException("Refusing to unload " + key.location() + " while players remain inside");
        }
        level.save(null, true, false);
        if (!levels(server).remove(key, level)) {
            throw new IllegalStateException("Runtime level map changed while unloading " + key.location());
        }
        server.markWorldsDirty();
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
        level.close();
        InstancedNotInfinite.LOGGER.info("Runtime level {} detached and closed", key.location());
    }

    @Override
    public boolean isLoaded(MinecraftServer server, ResourceKey<Level> key) {
        return levels(server).containsKey(key);
    }

    @Override
    public Path storagePath(MinecraftServer server, ResourceKey<Level> key) {
        return storage(server).getDimensionPath(key).toAbsolutePath().normalize();
    }

    public static ResourceKey<Level> levelKey(InstanceId id) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
            InstancedNotInfinite.MOD_ID, "instances/" + id.pathSegment());
        return ResourceKey.create(Registries.DIMENSION, location);
    }

    private static Holder<DimensionType> dimensionType(MinecraftServer server, EnvironmentType environment) {
        ResourceKey<DimensionType> key = switch (environment) {
            case NETHER_LIKE -> BuiltinDimensionTypes.NETHER;
            case END_LIKE -> BuiltinDimensionTypes.END;
            default -> BuiltinDimensionTypes.OVERWORLD;
        };
        return server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(key);
    }

    private static Holder<NoiseGeneratorSettings> noiseSettings(MinecraftServer server, EnvironmentType environment) {
        ResourceKey<NoiseGeneratorSettings> key = switch (environment) {
            case NETHER_LIKE -> NoiseGeneratorSettings.NETHER;
            case END_LIKE -> NoiseGeneratorSettings.END;
            case FLOATING_ISLAND -> NoiseGeneratorSettings.FLOATING_ISLANDS;
            default -> NoiseGeneratorSettings.OVERWORLD;
        };
        return server.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceKey<Level>, ServerLevel> levels(MinecraftServer server) {
        return ((MinecraftServerAccessor)(Object)server).instancednotinfinite$getLevels();
    }

    private static LevelStorageSource.LevelStorageAccess storage(MinecraftServer server) {
        return ((MinecraftServerAccessor)(Object)server).instancednotinfinite$getStorageSource();
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Dynamic level mutation must run on the Minecraft server thread");
        }
    }
}
