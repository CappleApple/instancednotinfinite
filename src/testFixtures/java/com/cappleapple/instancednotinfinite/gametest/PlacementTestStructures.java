package com.cappleapple.instancednotinfinite.gametest;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Deliberately unfamiliar structure type: core placement must not depend on IDs or an encoded start_height. */
@EventBusSubscriber(modid = InstancedNotInfinite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class PlacementTestStructures {
    private static final ResourceLocation ID = ResourceLocation.parse("instancednotinfinite:placement_test");
    private static final MapCodec<TestStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Structure.settingsCodec(instance), Codec.STRING.fieldOf("mode").forGetter(value -> value.mode)
    ).apply(instance, TestStructure::new));
    private static final StructureType<TestStructure> TYPE = () -> CODEC;
    private static final StructurePieceType PIECE = (context, tag) -> new TestPiece(tag);

    public static volatile PreparationGate preparationGate;

    /** A deterministic pause proves server ticks continue while layout work is still running. */
    public static final class PreparationGate {
        public final Thread serverThread = Thread.currentThread();
        public final java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        public final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        public final java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        public volatile boolean interrupted;
        public volatile boolean placedOnServer;

        public void awaitRelease() {
            if (Thread.currentThread() == serverThread) throw new IllegalStateException("Expensive generation ran on the server thread");
            started.countDown();
            try {
                if (!release.await(30, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test gate");
            } catch (InterruptedException exception) {
                interrupted = true;
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CancellationException("Test preparation interrupted");
            }
        }
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.STRUCTURE_TYPE, helper -> helper.register(ID, TYPE));
        event.register(Registries.STRUCTURE_PIECE, helper -> helper.register(ID, PIECE));
    }

    private static final class TestStructure extends Structure {
        private final String mode;

        TestStructure(StructureSettings settings, String mode) {
            super(settings);
            this.mode = mode;
        }

        @Override
        protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
            if (this.mode.startsWith("async_")) {
                PreparationGate gate = preparationGate;
                if (context.heightAccessor() instanceof net.minecraft.server.level.ServerLevel) {
                    throw new IllegalStateException("Worker received a live level");
                }
                gate.attempts.incrementAndGet();
                gate.awaitRelease();
                if (this.mode.equals("async_failure")) throw new IllegalStateException("Expected asynchronous layout failure");
                if (this.mode.equals("async_large") && (context.chunkPos().x != 1 || context.chunkPos().z != 1)) return Optional.empty();
            }
            int x = context.chunkPos().getMiddleBlockX();
            int z = context.chunkPos().getMiddleBlockZ();
            int y = switch (this.mode) {
                case "boat" -> context.chunkGenerator().getSeaLevel() - 5;
                case "seabed", "open_seabed" -> context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(), context.randomState()) - 1;
                case "sky", "sky_walled" -> 200;
                case "deferred" -> 120;
                default -> context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState()) - 1;
            };
            int radius = this.mode.equals("async_large") ? 64 : 5;
            return Optional.of(new GenerationStub(new BlockPos(x, y, z), builder -> builder.addPiece(new TestPiece(
                this.mode, new BoundingBox(x - radius, y, z - radius, x + radius, y + (this.mode.equals("seabed") ? 70 : 12), z + radius)))));
        }

        @Override
        public StructureType<?> type() {
            return TYPE;
        }
    }

    private static final class TestPiece extends StructurePiece {
        private final String mode;

        TestPiece(String mode, BoundingBox box) {
            super(PIECE, 0, box);
            this.mode = mode;
        }

        TestPiece(CompoundTag tag) {
            super(PIECE, tag);
            this.mode = tag.getString("Mode");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putString("Mode", this.mode);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            RandomSource random, BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
            if (this.mode.startsWith("async_")) {
                PreparationGate gate = preparationGate;
                if (Thread.currentThread() != gate.serverThread) throw new IllegalStateException("Live placement ran off the server thread");
                gate.placedOnServer = true;
            }
            BoundingBox box = getBoundingBox();
            if (this.mode.equals("deferred")) {
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, box.minX(), box.minZ()) - 1;
                box.move(0, surface - box.minY(), 0);
            }
            int deck = box.minY() + (this.mode.equals("boat") ? 6 : 0);
            for (int x = Math.max(box.minX(), chunk.minX()); x <= Math.min(box.maxX(), chunk.maxX()); x++) {
                for (int z = Math.max(box.minZ(), chunk.minZ()); z <= Math.min(box.maxZ(), chunk.maxZ()); z++) {
                    for (int y = deck; y <= deck + 6; y++) {
                        if (this.mode.equals("open_seabed") && y > deck) continue;
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!chunk.isInside(pos)) continue;
                        boolean edge = x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ();
                        boolean shell = this.mode.equals("seabed") && (y == deck + 6 || edge)
                            || this.mode.equals("sky_walled") && edge;
                        level.setBlock(pos, (y == deck || shell ? Blocks.STONE_BRICKS : Blocks.AIR).defaultBlockState(), 2);
                    }
                }
            }
            BlockPos center = box.getCenter();
            BlockPos keel = new BlockPos(center.getX(), box.minY(), center.getZ());
            if (chunk.isInside(keel)) level.setBlock(keel, Blocks.STONE_BRICKS.defaultBlockState(), 2);
            if (this.mode.equals("sky")) {
                // Same-state terrain write and a block entity must survive terrain removal.
                BlockPos marker = new BlockPos(center.getX(), 40, center.getZ());
                if (chunk.isInside(marker)) level.setBlock(marker, Blocks.STONE.defaultBlockState(), 2);
                BlockPos chest = new BlockPos(center.getX(), deck + 1, center.getZ());
                if (chunk.isInside(chest)) level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
            }
        }
    }
}
