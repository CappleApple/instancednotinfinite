package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.client.ClientManifestation.ClientVisualBlock;
import com.cappleapple.instancednotinfinite.snapshot.VisualLayer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Client disk cache for true-3D dungeon miniature source geometry. */
final class PersistentDungeonMiniatureCache {
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_BLOCKS = 2_000_000;
    private static final long MAX_NBT_BYTES = 256L * 1024L * 1024L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "InstancedNotInfinite-Miniature-Cache");
        thread.setDaemon(true);
        return thread;
    });
    private static final Set<ResourceLocation> ATTEMPTED = new java.util.HashSet<>();
    private static final Set<ResourceLocation> LOADING = new java.util.HashSet<>();
    private static final Map<ResourceLocation, Integer> PERSISTED_REVISIONS = new HashMap<>();
    private static UUID activeWorld;

    private PersistentDungeonMiniatureCache() {
    }

    static void setWorld(UUID worldId) {
        if (worldId.equals(activeWorld)) return;
        activeWorld = worldId;
        ATTEMPTED.clear();
        LOADING.clear();
        PERSISTED_REVISIONS.clear();
    }

    static void clearSession() {
        activeWorld = null;
        ATTEMPTED.clear();
        LOADING.clear();
        PERSISTED_REVISIONS.clear();
    }

    static void request(ResourceLocation dungeonId) {
        UUID worldId = activeWorld;
        if (worldId == null || !ATTEMPTED.add(dungeonId) || !LOADING.add(dungeonId)) return;
        Path path = path(worldId, dungeonId);
        CompletableFuture.supplyAsync(() -> read(path), IO).whenComplete((tag, failure) -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                if (!worldId.equals(activeWorld)) return;
                LOADING.remove(dungeonId);
                if (failure != null) {
                    InstancedNotInfinite.LOGGER.warn("Could not read cached dungeon miniature {}", dungeonId, failure);
                    return;
                }
                tag.flatMap(value -> decode(worldId, dungeonId, value)).ifPresent(value -> {
                    DungeonIconCache.loadedFromDisk(dungeonId, value);
                    InstancedNotInfinite.LOGGER.info(
                        "Loaded cached 3D miniature for {} ({} structure blocks)", dungeonId, value.blockCount());
                });
            });
        });
    }

    static void persist(ClientManifestation value) {
        UUID worldId = activeWorld;
        if (worldId == null || value.blockCount() == 0) return;
        Integer previous = PERSISTED_REVISIONS.put(value.dungeonId(), value.visualRevision());
        if (previous != null && previous == value.visualRevision()) return;

        CompoundTag encoded;
        try {
            encoded = encode(value);
        } catch (RuntimeException exception) {
            InstancedNotInfinite.LOGGER.warn("Could not encode 3D miniature cache for {}", value.dungeonId(), exception);
            return;
        }
        Path destination = path(worldId, value.dungeonId());
        CompletableFuture.runAsync(() -> write(destination, encoded), IO).exceptionally(failure -> {
            InstancedNotInfinite.LOGGER.warn("Could not persist 3D miniature cache for {}", value.dungeonId(), failure);
            return null;
        });
    }

    static void allowReload(ResourceLocation dungeonId) {
        ATTEMPTED.remove(dungeonId);
    }

    private static CompoundTag encode(ClientManifestation value) {
        List<ClientVisualBlock> source = value.snapshotBlocks().stream()
            .filter(block -> block.layer() == VisualLayer.STRUCTURE && !block.state().isAir())
            .toList();
        if (source.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("miniature exceeds " + MAX_BLOCKS + " structure blocks");
        }

        Map<BlockState, Integer> paletteIds = new LinkedHashMap<>();
        ListTag palette = new ListTag();
        int[] blocks = new int[source.size() * 4];
        int cursor = 0;
        for (ClientVisualBlock block : source) {
            Integer paletteId = paletteIds.get(block.state());
            if (paletteId == null) {
                paletteId = paletteIds.size();
                paletteIds.put(block.state(), paletteId);
                palette.add(NbtUtils.writeBlockState(block.state()));
            }
            blocks[cursor++] = block.position().getX();
            blocks[cursor++] = block.position().getY();
            blocks[cursor++] = block.position().getZ();
            blocks[cursor++] = paletteId;
        }

        CompoundTag root = new CompoundTag();
        root.putInt("Version", FORMAT_VERSION);
        root.putString("Dungeon", value.dungeonId().toString());
        root.put("Palette", palette);
        root.putIntArray("Blocks", blocks);
        return root;
    }

    private static Optional<ClientManifestation> decode(UUID worldId, ResourceLocation dungeonId, CompoundTag root) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
            || root.getInt("Version") != FORMAT_VERSION
            || !dungeonId.toString().equals(root.getString("Dungeon"))) {
            return Optional.empty();
        }
        ListTag paletteTags = root.getList("Palette", Tag.TAG_COMPOUND);
        if (paletteTags.isEmpty()) return Optional.empty();
        var blockRegistry = minecraft.level.registryAccess().lookupOrThrow(Registries.BLOCK);
        List<BlockState> palette = new ArrayList<>(paletteTags.size());
        for (int index = 0; index < paletteTags.size(); index++) {
            palette.add(NbtUtils.readBlockState(blockRegistry, paletteTags.getCompound(index)));
        }

        int[] packed = root.getIntArray("Blocks");
        if (packed.length == 0 || packed.length % 4 != 0 || packed.length / 4 > MAX_BLOCKS) {
            return Optional.empty();
        }
        List<ClientVisualBlock> blocks = new ArrayList<>(packed.length / 4);
        for (int cursor = 0; cursor < packed.length; cursor += 4) {
            int paletteId = packed[cursor + 3];
            if (paletteId < 0 || paletteId >= palette.size()) return Optional.empty();
            BlockState state = palette.get(paletteId);
            if (!state.isAir()) {
                blocks.add(new ClientVisualBlock(
                    new BlockPos(packed[cursor], packed[cursor + 1], packed[cursor + 2]),
                    state, VisualLayer.STRUCTURE, 0.0));
            }
        }
        if (blocks.isEmpty()) return Optional.empty();
        UUID id = UUID.nameUUIDFromBytes(
            (worldId + "/" + dungeonId).getBytes(StandardCharsets.UTF_8));
        return Optional.of(ClientManifestation.persistedMiniature(id, dungeonId, blocks));
    }

    private static Optional<CompoundTag> read(Path path) {
        if (!Files.isRegularFile(path)) return Optional.empty();
        try {
            return Optional.of(NbtIo.readCompressed(path, NbtAccounter.create(MAX_NBT_BYTES)));
        } catch (IOException | RuntimeException exception) {
            InstancedNotInfinite.LOGGER.warn("Ignoring unreadable dungeon miniature cache {}", path, exception);
            return Optional.empty();
        }
    }

    private static void write(Path destination, CompoundTag tag) {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        try {
            Files.createDirectories(destination.getParent());
            NbtIo.writeCompressed(tag, temporary);
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static Path path(UUID worldId, ResourceLocation dungeonId) {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("cache")
            .resolve(InstancedNotInfinite.MOD_ID)
            .resolve("miniatures")
            .resolve(worldId.toString())
            .resolve(fileName(dungeonId));
    }

    static String fileName(ResourceLocation dungeonId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(dungeonId.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest) + ".nbt";
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
