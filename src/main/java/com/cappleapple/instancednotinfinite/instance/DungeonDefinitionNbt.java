package com.cappleapple.instancednotinfinite.instance;

import com.cappleapple.instancednotinfinite.definition.BiomeRule;
import com.cappleapple.instancednotinfinite.definition.DecorationMode;
import com.cappleapple.instancednotinfinite.definition.DungeonDefinition;
import com.cappleapple.instancednotinfinite.definition.EntryPoint;
import com.cappleapple.instancednotinfinite.definition.EnvironmentType;
import com.cappleapple.instancednotinfinite.definition.HeightContext;
import com.cappleapple.instancednotinfinite.definition.PlacementMode;
import com.cappleapple.instancednotinfinite.definition.ReentryPolicy;
import com.cappleapple.instancednotinfinite.definition.StructureKind;
import com.cappleapple.instancednotinfinite.definition.TerrainSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class DungeonDefinitionNbt {
    private DungeonDefinitionNbt() {
    }

    static CompoundTag save(DungeonDefinition definition) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", definition.id());
        tag.putInt("FormatVersion", definition.formatVersion());
        tag.putString("Structure", definition.structure());
        tag.putString("StructureKind", definition.structureKind().name());
        tag.putInt("Weight", definition.weight());
        ListTag biomes = new ListTag();
        for (BiomeRule rule : definition.biomes()) {
            CompoundTag biome = new CompoundTag();
            biome.putString("Reference", rule.reference());
            biome.putInt("Weight", rule.weight());
            biomes.add(biome);
        }
        tag.put("Biomes", biomes);
        tag.putInt("HeightMin", definition.height().min());
        tag.putInt("HeightMax", definition.height().max());
        tag.putString("Environment", definition.environment().name());
        if (definition.customEnvironment() != null) {
            tag.putString("CustomEnvironment", definition.customEnvironment());
        }
        tag.putInt("HorizontalPadding", definition.terrain().horizontalPadding());
        tag.putInt("VerticalPadding", definition.terrain().verticalPadding());
        tag.putInt("MaximumRadius", definition.terrain().maximumRadius());
        if (definition.portal().innerColor() != null) tag.putString("PortalInnerColor", definition.portal().innerColor());
        if (definition.portal().outerColor() != null) tag.putString("PortalOuterColor", definition.portal().outerColor());
        tag.putInt("EntryX", definition.entry().x());
        tag.putInt("EntryY", definition.entry().y());
        tag.putInt("EntryZ", definition.entry().z());
        tag.putFloat("EntryYaw", definition.entry().yaw());
        tag.putFloat("EntryPitch", definition.entry().pitch());
        tag.putString("Placement", definition.placement().name());
        tag.putString("Decoration", definition.decoration().name());
        tag.putBoolean("NaturalSpawning", definition.allowNaturalMobSpawning());
        tag.putString("Reentry", definition.reentry().name());
        return tag;
    }

    static DungeonDefinition load(CompoundTag tag) {
        List<BiomeRule> biomes = new ArrayList<>();
        ListTag biomeTags = tag.getList("Biomes", Tag.TAG_COMPOUND);
        for (int index = 0; index < biomeTags.size(); index++) {
            CompoundTag biome = biomeTags.getCompound(index);
            biomes.add(new BiomeRule(biome.getString("Reference"), biome.getInt("Weight")));
        }
        String custom = tag.contains("CustomEnvironment", Tag.TAG_STRING) ? tag.getString("CustomEnvironment") : null;
        return new DungeonDefinition(
            tag.getString("Id"),
            tag.getInt("FormatVersion"),
            tag.getString("Structure"),
            enumValue(StructureKind.class, tag.getString("StructureKind"), StructureKind.AUTO),
            tag.getInt("Weight"),
            biomes,
            new HeightContext(tag.getInt("HeightMin"), tag.getInt("HeightMax")),
            enumValue(EnvironmentType.class, tag.getString("Environment"), EnvironmentType.SURFACE),
            custom,
            new TerrainSettings(tag.getInt("HorizontalPadding"), tag.getInt("VerticalPadding"), tag.getInt("MaximumRadius")),
            new com.cappleapple.instancednotinfinite.definition.PortalSettings(
                tag.contains("PortalInnerColor", Tag.TAG_STRING) ? tag.getString("PortalInnerColor") : null,
                tag.contains("PortalOuterColor", Tag.TAG_STRING) ? tag.getString("PortalOuterColor") : null),
            new EntryPoint(
                tag.getInt("EntryX"), tag.getInt("EntryY"), tag.getInt("EntryZ"),
                tag.getFloat("EntryYaw"), tag.getFloat("EntryPitch")),
            enumValue(PlacementMode.class, tag.getString("Placement"), PlacementMode.DIRECT),
            enumValue(DecorationMode.class, tag.getString("Decoration"), DecorationMode.SAFE),
            tag.getBoolean("NaturalSpawning"),
            enumValue(ReentryPolicy.class, tag.getString("Reentry"), ReentryPolicy.WHILE_ACTIVE));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
