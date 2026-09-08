package com.cappleapple.instancednotinfinite.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/** Unload cleanup must scale with loaded entities rather than the area of an entire dimension. */
public final class LoadedEntityQuery {
    private LoadedEntityQuery() { }

    public static <T extends Entity> List<T> matching(ServerLevel level, Class<T> type, AABB bounds, Predicate<? super T> filter) {
        List<T> matches = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!type.isInstance(entity)) continue;
            T candidate = type.cast(entity);
            if (SableCoordinates.worldBounds(level, entity.blockPosition(), entity.getBoundingBox()).intersects(bounds)
                && filter.test(candidate)) matches.add(candidate);
        }
        return matches;
    }
}
