package com.cappleapple.instancednotinfinite.content;

import com.cappleapple.instancednotinfinite.manifestation.DungeonTarget;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public record ManifestationTargetComponent(String kind, Optional<ResourceLocation> id) {
    public static final Codec<ManifestationTargetComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("kind").forGetter(ManifestationTargetComponent::kind),
        ResourceLocation.CODEC.optionalFieldOf("id").forGetter(ManifestationTargetComponent::id)
    ).apply(instance, ManifestationTargetComponent::new));

    public ManifestationTargetComponent {
        kind = kind.toLowerCase(Locale.ROOT);
        id = id == null ? Optional.empty() : id;
        if (!kind.equals("dungeon") && !kind.equals("pool") && !kind.equals("structure_pool")) {
            throw new IllegalArgumentException("Manifestation target kind must be dungeon, pool, or structure_pool");
        }
        if ((kind.equals("dungeon") || kind.equals("structure_pool")) != id.isPresent()) {
            throw new IllegalArgumentException("Dungeon and structure-pool targets require id; configured pool targets must omit it");
        }
    }

    public static ManifestationTargetComponent dungeon(ResourceLocation id) {
        return new ManifestationTargetComponent("dungeon", Optional.of(id));
    }

    public static ManifestationTargetComponent pool() {
        return new ManifestationTargetComponent("pool", Optional.empty());
    }

    public static ManifestationTargetComponent structurePool(ResourceLocation tagId) {
        return new ManifestationTargetComponent("structure_pool", Optional.of(tagId));
    }

    public static ManifestationTargetComponent fromTarget(DungeonTarget target) {
        return switch (target.kind()) {
            case DUNGEON -> dungeon(target.id().orElseThrow());
            case CONFIGURED_POOL -> pool();
            case STRUCTURE_POOL -> structurePool(target.id().orElseThrow());
        };
    }

    public DungeonTarget target() {
        return switch (kind) {
            case "dungeon" -> DungeonTarget.dungeon(id.orElseThrow());
            case "structure_pool" -> DungeonTarget.structurePool(id.orElseThrow());
            default -> DungeonTarget.configuredPool();
        };
    }
}
