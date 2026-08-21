package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.client.ClientManifestation.ClientVisualBlock;
import com.cappleapple.instancednotinfinite.snapshot.VisualLayer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * Converts immutable structure snapshots into face-culled static GPU buffers. Building is
 * time-sliced across client ticks; rendering performs no per-block visibility or model work.
 */
public final class HologramMeshCache {
    static final int REVEAL_BUCKETS = 64;
    private static final int MAX_BLOCKS_PER_BUILD_TICK = 2048;
    private static final long BUILD_BUDGET_NANOS = 4_000_000L;
    private static final int MAX_SPECIAL_MODELS = 128;
    /*
     * Baked block quads must remain in BLOCK layout. Entity sheets use NEW_ENTITY layout;
     * caching block data through that path can leave valid positions/colors but corrupt the
     * atlas UV stream, producing flat white silhouettes. This main-target block pass works
     * both during world rendering and while an icon framebuffer is explicitly bound.
     */
    private static final RenderType DRAW_TYPE = RenderType.create(
        InstancedNotInfinite.MOD_ID + "_hologram",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        RenderType.SMALL_BUFFER_SIZE,
        true,
        false,
        RenderType.CompositeState.builder()
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
            .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setCullState(RenderStateShard.CULL)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .createCompositeState(true));
    private static final Map<UUID, CacheEntry> ENTRIES = new LinkedHashMap<>();
    private static long clientTick;

    private HologramMeshCache() {
    }

    static void markDirty(ClientManifestation value) {
        ENTRIES.computeIfAbsent(value.id(), ignored -> new CacheEntry()).value = value;
    }

    static void requestBuild(ClientManifestation value) {
        CacheEntry entry = ENTRIES.computeIfAbsent(value.id(), ignored -> new CacheEntry());
        entry.value = value;
        entry.requestedUntilTick = clientTick + 40;
    }

    static boolean isCurrent(ClientManifestation value) {
        requestBuild(value);
        CacheEntry entry = ENTRIES.get(value.id());
        return entry != null && entry.mesh != null && entry.builtRevision == value.visualRevision();
    }

    public static boolean render(ClientManifestation value, PoseStack pose, float progress) {
        requestBuild(value);
        CacheEntry entry = ENTRIES.get(value.id());
        if (entry == null || entry.mesh == null) return false;
        entry.mesh.render(pose, progress);
        return true;
    }

    static void tick() {
        clientTick++;
        RenderSystem.assertOnRenderThread();
        ENTRIES.values().forEach(HologramMeshCache::logCompletedRevision);

        for (CacheEntry entry : ENTRIES.values()) {
            if (entry.job == null) continue;
            try {
                Optional<CachedMesh> complete = entry.job.advance();
                if (complete.isPresent()) install(entry, complete.get());
            } catch (RuntimeException exception) {
                int failedRevision = entry.job.revision;
                entry.job.close();
                entry.job = null;
                entry.retryAfterTick = clientTick + 100;
                InstancedNotInfinite.LOGGER.warn("Could not build hologram mesh revision {}", failedRevision, exception);
            }
            return; // Globally bound preprocessing to one time-sliced job per client tick.
        }

        for (CacheEntry entry : ENTRIES.values()) {
            if (entry.value == null || entry.requestedUntilTick < clientTick || clientTick < entry.retryAfterTick) continue;
            if (shouldBuild(entry)) {
                entry.job = new BuildJob(entry.value);
                try {
                    Optional<CachedMesh> complete = entry.job.advance();
                    if (complete.isPresent()) install(entry, complete.get());
                } catch (RuntimeException exception) {
                    int failedRevision = entry.job.revision;
                    entry.job.close();
                    entry.job = null;
                    entry.retryAfterTick = clientTick + 100;
                    InstancedNotInfinite.LOGGER.warn("Could not build hologram mesh revision {}", failedRevision, exception);
                }
                return;
            }
        }
    }

    private static boolean shouldBuild(CacheEntry entry) {
        ClientManifestation value = entry.value;
        int count = value.blockCount();
        if (count == 0 || entry.builtRevision == value.visualRevision()) return false;
        if (entry.mesh == null) {
            return value.generationComplete() || count >= 256 || value.ticksSinceBlockUpdate() >= 4;
        }
        if (value.generationComplete()) return true;
        int growthThreshold = Math.max(1024, Math.max(1, entry.builtBlockCount / 2));
        return count >= entry.builtBlockCount + growthThreshold
            || value.ticksSinceBlockUpdate() >= 10;
    }

    private static void install(CacheEntry entry, CachedMesh mesh) {
        if (entry.mesh != null) entry.mesh.close();
        entry.mesh = mesh;
        entry.builtRevision = entry.job.revision;
        entry.builtBlockCount = entry.job.sourceBlockCount;
        entry.job = null;
        entry.retryAfterTick = 0;

        if (entry.value.generationComplete() && entry.builtRevision == entry.value.visualRevision()) {
            logCompletedRevision(entry);
        } else {
            logMesh(entry, false);
        }
    }

    private static void logCompletedRevision(CacheEntry entry) {
        if (entry.value == null || entry.mesh == null
            || !entry.value.generationComplete()
            || entry.builtRevision != entry.value.visualRevision()
            || entry.loggedRevision == entry.builtRevision) {
            return;
        }
        logMesh(entry, true);
        entry.loggedRevision = entry.builtRevision;
    }

    private static void logMesh(CacheEntry entry, boolean completed) {
        MeshStats stats = entry.mesh.stats;
        double blockCulledPercent = stats.sourceBlocks == 0
            ? 0.0
            : (stats.sourceBlocks - stats.retainedBlocks) * 100.0 / stats.sourceBlocks;
        long maximumDirectionalFaces = (long)stats.sourceBlocks * Direction.values().length;
        double faceCulledPercent = maximumDirectionalFaces == 0
            ? 0.0
            : (maximumDirectionalFaces - stats.directionalFaces) * 100.0 / maximumDirectionalFaces;
        String message = "Hologram mesh {}: source={} retained={} ({}% block culled), directionalFaces={} "
            + "({}% face culled), quads={}, legacyModelCalls/frame={}, drawCalls<={}, specialModels={}, "
            + "enclosed={}, failedModels={}, build={}ms, upload={}ms, gpu={}KiB";
        Object[] arguments = {
            entry.value.dungeonId(), stats.sourceBlocks, stats.retainedBlocks,
            String.format(java.util.Locale.ROOT, "%.1f", blockCulledPercent), stats.directionalFaces,
            String.format(java.util.Locale.ROOT, "%.1f", faceCulledPercent), stats.quads,
            stats.sourceBlocks, stats.nonEmptyBuckets, stats.specialModels, stats.enclosedBlocks,
            stats.failedModels,
            String.format(java.util.Locale.ROOT, "%.2f", stats.buildNanos / 1_000_000.0),
            String.format(java.util.Locale.ROOT, "%.2f", stats.uploadNanos / 1_000_000.0),
            stats.gpuBytes / 1024
        };
        if (completed) {
            InstancedNotInfinite.LOGGER.info(message, arguments);
        } else {
            InstancedNotInfinite.LOGGER.debug(message, arguments);
        }
    }

    static void remove(UUID id) {
        if (DungeonIconCache.retains(id)) return;
        CacheEntry entry = ENTRIES.remove(id);
        if (entry != null) entry.close();
    }

    static void clear() {
        ENTRIES.values().forEach(CacheEntry::close);
        ENTRIES.clear();
    }

    private static final class CacheEntry implements AutoCloseable {
        private ClientManifestation value;
        private CachedMesh mesh;
        private BuildJob job;
        private int builtRevision = -1;
        private int builtBlockCount;
        private int loggedRevision = -1;
        private long requestedUntilTick;
        private long retryAfterTick;

        @Override
        public void close() {
            if (mesh != null) mesh.close();
            if (job != null) job.close();
            mesh = null;
            job = null;
        }
    }

    private static final class BuildJob implements AutoCloseable {
        private final int revision;
        private final int sourceBlockCount;
        private final List<ClientVisualBlock> blocks;
        private final Map<Long, ClientVisualBlock> byPosition;
        private final Map<Integer, MeshAccumulator> accumulators = new HashMap<>();
        private final List<ClientVisualBlock> specialModels = new ArrayList<>();
        private final BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        private final RandomSource random = RandomSource.create(42L);
        private final PoseStack blockPose = new PoseStack();
        private int cursor;
        private int retainedBlocks;
        private int enclosedBlocks;
        private int directionalFaces;
        private int quads;
        private int failedModels;
        private long activeBuildNanos;
        private boolean closed;

        private BuildJob(ClientManifestation value) {
            this.revision = value.visualRevision();
            this.blocks = value.snapshotBlocks();
            this.sourceBlockCount = blocks.size();
            this.byPosition = new HashMap<>(Math.max(16, sourceBlockCount * 2));
            for (ClientVisualBlock block : blocks) byPosition.put(block.position().asLong(), block);
        }

        private Optional<CachedMesh> advance() {
            if (closed) throw new IllegalStateException("Hologram build job is closed");
            long started = System.nanoTime();
            int processed = 0;
            while (cursor < blocks.size() && processed < MAX_BLOCKS_PER_BUILD_TICK) {
                process(blocks.get(cursor++));
                processed++;
                if (System.nanoTime() - started >= BUILD_BUDGET_NANOS) break;
            }
            activeBuildNanos += System.nanoTime() - started;
            if (cursor < blocks.size()) return Optional.empty();
            return Optional.of(upload());
        }

        private void process(ClientVisualBlock block) {
            if (block.layer() != VisualLayer.STRUCTURE || block.state().isAir()) return;
            BlockState state = block.state();
            RenderShape shape = state.getRenderShape();
            if (shape == RenderShape.ENTITYBLOCK_ANIMATED) {
                if (specialModels.size() < MAX_SPECIAL_MODELS) {
                    specialModels.add(block);
                    retainedBlocks++;
                }
                return;
            }
            if (shape != RenderShape.MODEL) return;

            Direction[] directions = Direction.values();
            int faceMask = HologramMeshPlanner.visibleFaceMask(directions.length, face -> {
                Direction direction = directions[face];
                return isFaceOccluded(state, block.position().relative(direction), direction);
            });
            int before = quads;
            try {
                BakedModel model = blockRenderer.getBlockModel(state);
                int color = Minecraft.getInstance().getBlockColors().getColor(state, null, null, 0);
                float red = (color >> 16 & 0xFF) / 255.0F;
                float green = (color >> 8 & 0xFF) / 255.0F;
                float blue = (color & 0xFF) / 255.0F;
                int bucket = HologramMeshPlanner.bucket(block.score(), REVEAL_BUCKETS);
                blockPose.pushPose();
                try {
                    blockPose.translate(block.position().getX(), block.position().getY(), block.position().getZ());
                    random.setSeed(42L);
                    for (RenderType renderType : model.getRenderTypes(state, random, ModelData.EMPTY)) {
                        for (Direction direction : directions) {
                            if (!HologramMeshPlanner.includes(faceMask, direction.ordinal())) continue;
                            random.setSeed(42L);
                            List<BakedQuad> directional = model.getQuads(state, direction, random, ModelData.EMPTY, renderType);
                            if (!directional.isEmpty()) directionalFaces++;
                            emit(bucket, directional, red, green, blue);
                        }
                        random.setSeed(42L);
                        emit(bucket, model.getQuads(state, null, random, ModelData.EMPTY, renderType), red, green, blue);
                    }
                } finally {
                    blockPose.popPose();
                }
            } catch (RuntimeException exception) {
                failedModels++;
                // A single malformed third-party model must not invalidate the whole preview.
            }
            if (quads > before) retainedBlocks++;
            else if (faceMask == 0) enclosedBlocks++;
        }

        private boolean isFaceOccluded(BlockState source, BlockPos neighborPosition, Direction direction) {
            ClientVisualBlock neighborBlock = byPosition.get(neighborPosition.asLong());
            if (neighborBlock == null) return false;
            BlockState neighbor = neighborBlock.state();
            try {
                if (source.skipRendering(neighbor, direction)) return true;
                return neighbor.canOcclude() && Block.isShapeFullBlock(
                    neighbor.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        private void emit(int bucket, List<BakedQuad> bakedQuads, float red, float green, float blue) {
            if (bakedQuads.isEmpty()) return;
            MeshAccumulator accumulator = accumulators.computeIfAbsent(bucket, ignored -> new MeshAccumulator());
            for (BakedQuad quad : bakedQuads) {
                float tintRed = quad.isTinted() ? red : 1.0F;
                float tintGreen = quad.isTinted() ? green : 1.0F;
                float tintBlue = quad.isTinted() ? blue : 1.0F;
                accumulator.builder.putBulkData(
                    blockPose.last(), quad, tintRed, tintGreen, tintBlue, 1.0F,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                accumulator.quads++;
                quads++;
            }
        }

        private CachedMesh upload() {
            long started = System.nanoTime();
            VertexBuffer[] buffers = new VertexBuffer[REVEAL_BUCKETS];
            long gpuBytes = 0;
            int nonEmptyBuckets = 0;
            try {
                for (Map.Entry<Integer, MeshAccumulator> entry : accumulators.entrySet()) {
                    MeshAccumulator accumulator = entry.getValue();
                    MeshData mesh = accumulator.builder.build();
                    if (mesh == null) continue;
                    VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    buffer.bind();
                    try {
                        buffer.upload(mesh);
                    } finally {
                        VertexBuffer.unbind();
                    }
                    buffers[entry.getKey()] = buffer;
                    nonEmptyBuckets++;
                    gpuBytes += (long)accumulator.quads * 4L * DRAW_TYPE.format().getVertexSize();
                }
            } catch (RuntimeException exception) {
                for (VertexBuffer buffer : buffers) if (buffer != null) buffer.close();
                throw exception;
            } finally {
                accumulators.values().forEach(MeshAccumulator::close);
                accumulators.clear();
            }
            long uploadNanos = System.nanoTime() - started;
            MeshStats stats = new MeshStats(
                sourceBlockCount, retainedBlocks, enclosedBlocks, directionalFaces, quads,
                nonEmptyBuckets, specialModels.size(), failedModels, activeBuildNanos, uploadNanos, gpuBytes);
            closed = true;
            return new CachedMesh(buffers, List.copyOf(specialModels), stats);
        }

        @Override
        public void close() {
            if (closed) return;
            accumulators.values().forEach(MeshAccumulator::close);
            accumulators.clear();
            closed = true;
        }
    }

    private static final class MeshAccumulator implements AutoCloseable {
        private final ByteBufferBuilder bytes = new ByteBufferBuilder(4096);
        private final BufferBuilder builder = new BufferBuilder(bytes, DRAW_TYPE.mode(), DRAW_TYPE.format());
        private int quads;

        @Override
        public void close() {
            bytes.close();
        }
    }

    private static final class CachedMesh implements AutoCloseable {
        private final VertexBuffer[] buckets;
        private final List<ClientVisualBlock> specialModels;
        private final MeshStats stats;

        private CachedMesh(VertexBuffer[] buckets, List<ClientVisualBlock> specialModels, MeshStats stats) {
            this.buckets = buckets;
            this.specialModels = specialModels;
            this.stats = stats;
        }

        private void render(PoseStack pose, float progress) {
            int visibleBuckets = HologramMeshPlanner.visibleBucketCount(progress, REVEAL_BUCKETS);
            if (visibleBuckets > 0) {
                DRAW_TYPE.setupRenderState();
                ShaderInstance shader = Objects.requireNonNull(RenderSystem.getShader(), "hologram shader");
                Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
                try {
                    shader.setDefaultUniforms(
                        DRAW_TYPE.mode(), modelView, RenderSystem.getProjectionMatrix(), Minecraft.getInstance().getWindow());
                    // This stage inherits terrain fog. The miniature is already camera-relative and
                    // heavily scaled, so applying that fog a second time can blend every ordinary
                    // block quad completely into the current sky colour. Block entities use their
                    // own buffered pass, which is why they remained textured while the cached mesh
                    // looked like a piece of the skybox.
                    if (shader.FOG_START != null) shader.FOG_START.set(1_000_000.0F);
                    if (shader.FOG_END != null) shader.FOG_END.set(1_000_001.0F);
                    if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
                    shader.setSampler(
                        "Sampler0",
                        Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS));
                    shader.apply();
                    for (int index = 0; index < visibleBuckets; index++) {
                        VertexBuffer buffer = buckets[index];
                        if (buffer == null) continue;
                        buffer.bind();
                        buffer.draw();
                    }
                } finally {
                    VertexBuffer.unbind();
                    shader.clear();
                    DRAW_TYPE.clearRenderState();
                }
            }
            renderSpecialModels(pose, progress);
        }

        private void renderSpecialModels(PoseStack pose, float progress) {
            if (specialModels.isEmpty()) return;
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            for (ClientVisualBlock block : specialModels) {
                if (block.score() > progress) continue;
                pose.pushPose();
                pose.translate(block.position().getX(), block.position().getY(), block.position().getZ());
                try {
                    Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                        block.state(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                } catch (RuntimeException ignored) {
                }
                pose.popPose();
            }
            buffers.endBatch();
        }

        @Override
        public void close() {
            for (VertexBuffer buffer : buckets) if (buffer != null) buffer.close();
        }
    }

    private record MeshStats(
        int sourceBlocks,
        int retainedBlocks,
        int enclosedBlocks,
        int directionalFaces,
        int quads,
        int nonEmptyBuckets,
        int specialModels,
        int failedModels,
        long buildNanos,
        long uploadNanos,
        long gpuBytes
    ) {
    }
}
