package com.cappleapple.instancednotinfinite.cleanup;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.backend.VanillaDynamicLevelBackend;
import com.cappleapple.instancednotinfinite.instance.InstanceId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class InstanceCleanupManager implements AutoCloseable {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "InstancedNotInfinite-Cleanup");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<InstanceId> inFlight = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();

    public boolean request(MinecraftServer server, InstanceId id, Path storagePath) {
        if (!this.inFlight.add(id)) {
            return false;
        }
        Path root = server.getWorldPath(LevelResource.ROOT)
            .resolve("dimensions")
            .resolve(InstancedNotInfinite.MOD_ID)
            .resolve("instances");
        Path target;
        try {
            target = CleanupPathGuard.requireOwnedTarget(root, storagePath);
            validateMarker(target, id);
        } catch (Exception exception) {
            this.inFlight.remove(id);
            this.results.add(new Result(id, false, exception));
            return false;
        }
        this.executor.execute(() -> delete(target, id));
        return true;
    }

    public Result poll() {
        return this.results.poll();
    }

    public boolean inFlight(InstanceId id) {
        return this.inFlight.contains(id);
    }

    private void delete(Path target, InstanceId id) {
        Exception failure = null;
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                try (var paths = Files.walk(target)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(path);
                    }
                }
            }
        } catch (Exception exception) {
            failure = exception;
        } finally {
            this.inFlight.remove(id);
            this.results.add(new Result(id, failure == null, failure));
        }
    }

    private static void validateMarker(Path target, InstanceId id) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(target)) {
            throw new SecurityException("Refusing cleanup of symbolic-link instance directory " + target);
        }
        Path marker = target.resolve(VanillaDynamicLevelBackend.MARKER_FILE);
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw new SecurityException("Instance marker is missing from " + target);
        }
        String contents = Files.readString(marker, StandardCharsets.UTF_8);
        if (!contents.contains("mod=" + InstancedNotInfinite.MOD_ID + "\n")
            || !contents.contains("instance=" + id + "\n")
            || !contents.contains("schema=1\n")) {
            throw new SecurityException("Instance marker does not match " + id);
        }
    }

    @Override
    public void close() {
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(5, TimeUnit.SECONDS)) {
                InstancedNotInfinite.LOGGER.warn("Dungeon cleanup executor did not finish within five seconds; pending deletion will recover next start");
                this.executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.executor.shutdownNow();
        }
    }

    public record Result(InstanceId id, boolean success, Exception failure) {
    }
}
