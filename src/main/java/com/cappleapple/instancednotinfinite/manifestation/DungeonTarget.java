package com.cappleapple.instancednotinfinite.manifestation;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Identifies one exact dungeon, the configured weighted catalogue, or a named structure-tag pool. */
public record DungeonTarget(Kind kind, Optional<ResourceLocation> id) {
    public DungeonTarget {
        Objects.requireNonNull(kind, "kind");
        id = Objects.requireNonNull(id, "id");
        if ((kind == Kind.DUNGEON || kind == Kind.STRUCTURE_POOL) && id.isEmpty()) {
            throw new IllegalArgumentException(kind + " target requires an id");
        }
        if (kind == Kind.CONFIGURED_POOL && id.isPresent()) {
            throw new IllegalArgumentException("The built-in configured pool does not have a named id");
        }
    }

    public static DungeonTarget dungeon(ResourceLocation id) {
        return new DungeonTarget(Kind.DUNGEON, Optional.of(id));
    }

    public static DungeonTarget configuredPool() {
        return new DungeonTarget(Kind.CONFIGURED_POOL, Optional.empty());
    }

    public static DungeonTarget structurePool(ResourceLocation tagId) {
        return new DungeonTarget(Kind.STRUCTURE_POOL, Optional.of(tagId));
    }

    public enum Kind {
        DUNGEON,
        CONFIGURED_POOL,
        STRUCTURE_POOL
    }
}
