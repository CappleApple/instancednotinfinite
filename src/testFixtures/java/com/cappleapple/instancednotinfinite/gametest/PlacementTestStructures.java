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
            return Optional.of(new GenerationStub(new BlockPos(x, y, z), builder -> builder.addPiece(new TestPiece(
                this.mode, new BoundingBox(x - 5, y, z - 5, x + 5, y + (this.mode.equals("seabed") ? 70 : 12), z + 5)))));
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
            BoundingBox box = getBoundingBox();
            if (this.mode.equals("deferred")) {
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, box.minX(), box.minZ()) - 1;
                box.move(0, surface - box.minY(), 0);
            }
            int deck = box.minY() + (this.mode.equals("boat") ? 6 : 0);
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
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
