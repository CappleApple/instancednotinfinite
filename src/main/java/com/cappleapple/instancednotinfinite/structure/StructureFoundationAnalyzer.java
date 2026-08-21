package com.cappleapple.instancednotinfinite.structure;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.terrain.DominantFoundationLevel;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Reads authored template geometry to locate the real base of large multi-piece structures. */
public final class StructureFoundationAnalyzer {
    private StructureFoundationAnalyzer() {
    }

    public static OptionalInt infer(ServerLevel level, StructureStart start) {
        return profile(level, start).map(value -> OptionalInt.of(value.foundation().baseY())).orElseGet(OptionalInt::empty);
    }

    public static Optional<FoundationProfile> profile(ServerLevel level, StructureStart start) {
        Map<StructureTemplate, List<BlockPos>> solidBlocks = new IdentityHashMap<>();
        Map<Integer, Set<Long>> coverageByY = new HashMap<>();
        Map<Integer, Integer> placementGroundCounts = new HashMap<>();

        for (StructurePiece piece : start.getPieces()) {
            int placementGroundY = piece instanceof PoolElementStructurePiece poolPiece
                ? piece.getBoundingBox().minY() + poolPiece.getGroundLevelDelta()
                : piece.getBoundingBox().minY();
            placementGroundCounts.merge(placementGroundY, 1, Integer::sum);
            if (piece instanceof TemplateStructurePiece templatePiece) {
                addTemplate(
                    level,
                    templatePiece.template(),
                    templatePiece.templatePosition(),
                    templatePiece.placeSettings(),
                    solidBlocks,
                    coverageByY);
            } else if (piece instanceof PoolElementStructurePiece poolPiece) {
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(poolPiece.getRotation());
                for (ResourceLocation templateId : templateLocations(level, poolPiece.getElement())) {
                    level.getStructureManager().get(templateId).ifPresent(template -> addTemplate(
                        level, template, poolPiece.getPosition(), settings, solidBlocks, coverageByY));
                }
            }
        }

        Map<Integer, Integer> counts = new HashMap<>();
        coverageByY.forEach((y, columns) -> counts.put(y, columns.size()));
        int placementGroundY = placementGroundCounts.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .mapToInt(Map.Entry::getKey)
            .findFirst()
            .orElse(start.getBoundingBox().minY());
        return DominantFoundationLevel.inferSpan(counts)
            .map(foundation -> new FoundationProfile(foundation, placementGroundY));
    }

    private static void addTemplate(
        ServerLevel level,
        StructureTemplate template,
        BlockPos origin,
        StructurePlaceSettings settings,
        Map<StructureTemplate, List<BlockPos>> solidBlocks,
        Map<Integer, Set<Long>> coverageByY
    ) {
        List<BlockPos> blocks = solidBlocks.computeIfAbsent(template, ignored -> readSolidBlocks(level, template));
        for (BlockPos local : blocks) {
            BlockPos world = StructureTemplate.calculateRelativePosition(settings, local).offset(origin);
            coverageByY.computeIfAbsent(world.getY(), ignored -> new HashSet<>()).add(BlockPos.asLong(world.getX(), 0, world.getZ()));
        }
    }

    private static List<BlockPos> readSolidBlocks(ServerLevel level, StructureTemplate template) {
        CompoundTag data = template.save(new CompoundTag());
        ListTag palette = data.getList("palette", Tag.TAG_COMPOUND);
        if (palette.isEmpty()) {
            ListTag palettes = data.getList("palettes", Tag.TAG_LIST);
            if (!palettes.isEmpty()) {
                palette = palettes.getList(0);
            }
        }
        List<BlockState> states = new ArrayList<>(palette.size());
        for (Tag value : palette) {
            states.add(NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK), (CompoundTag) value));
        }

        List<BlockPos> result = new ArrayList<>();
        for (Tag value : data.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) value;
            int stateId = block.getInt("state");
            if (stateId < 0 || stateId >= states.size()) {
                continue;
            }
            BlockState state = states.get(stateId);
            if (!state.getFluidState().isEmpty()
                || !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                continue;
            }
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            result.add(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)));
        }
        return List.copyOf(result);
    }

    private static Set<ResourceLocation> templateLocations(ServerLevel level, StructurePoolElement element) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        try {
            DynamicOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            StructurePoolElement.CODEC.encodeStart(ops, element).resultOrPartial(message ->
                InstancedNotInfinite.LOGGER.debug("Could not inspect structure pool element geometry: {}", message))
                .ifPresent(tag -> collectLocations(tag, result));
        } catch (RuntimeException exception) {
            InstancedNotInfinite.LOGGER.debug("Could not inspect structure pool element geometry", exception);
        }
        return result;
    }

    private static void collectLocations(Tag tag, Set<ResourceLocation> result) {
        if (tag instanceof CompoundTag compound) {
            Tag location = compound.get("location");
            if (location instanceof StringTag string) {
                ResourceLocation parsed = ResourceLocation.tryParse(string.getAsString());
                if (parsed != null) {
                    result.add(parsed);
                }
            }
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (child != null && child != location) {
                    collectLocations(child, result);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                collectLocations(child, result);
            }
        }
    }

    public record FoundationProfile(
        DominantFoundationLevel.FoundationSpan foundation,
        int placementGroundY
    ) {
    }
}
