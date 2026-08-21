package com.cappleapple.instancednotinfinite.client;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.backend.InstanceDimensionIds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/** Dimension-scoped client presentation guard for the optional Distant Horizons integration. */
public final class DistantHorizonsClientCompat {
    private static boolean loggedSuppressed;

    private DistantHorizonsClientCompat() {
    }

    public static boolean shouldSuppress() {
        return shouldSuppress(Minecraft.getInstance().level);
    }

    public static boolean shouldSuppress(Level level) {
        return level != null && InstanceDimensionIds.isTemporaryInstance(level.dimension());
    }

    static void tick() {
        if (!ModList.get().isLoaded("distanthorizons")) return;
        boolean suppressed = shouldSuppress();
        if (suppressed == loggedSuppressed) return;
        loggedSuppressed = suppressed;
        if (suppressed) {
            InstancedNotInfinite.LOGGER.info(
                "Suspended Distant Horizons client rendering inside temporary dungeon dimension {}",
                Minecraft.getInstance().level.dimension().location());
        } else {
            InstancedNotInfinite.LOGGER.info("Resumed Distant Horizons client rendering outside temporary dungeon dimensions");
        }
    }

    static void reset() {
        loggedSuppressed = false;
    }
}
