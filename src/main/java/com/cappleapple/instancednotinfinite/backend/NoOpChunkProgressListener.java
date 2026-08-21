package com.cappleapple.instancednotinfinite.backend;

import javax.annotation.Nullable;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

final class NoOpChunkProgressListener implements ChunkProgressListener {
    static final NoOpChunkProgressListener INSTANCE = new NoOpChunkProgressListener();

    private NoOpChunkProgressListener() {
    }

    @Override
    public void updateSpawnPos(ChunkPos pos) {
    }

    @Override
    public void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status) {
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
