package com.cappleapple.instancednotinfinite.manifestation;

import com.cappleapple.instancednotinfinite.snapshot.VisualBlock;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Deterministic spatial ordering shared by hologram and miniature render paths. */
public final class ManifestationScorer {
    private ManifestationScorer() {
    }

    public static AnimationMode resolveMode(AnimationMode requested, long animationSeed, java.util.List<AnimationMode> allowed) {
        if (requested != AnimationMode.RANDOM_MODE) {
            return requested;
        }
        java.util.List<AnimationMode> usable = allowed.stream()
            .filter(mode -> mode != AnimationMode.RANDOM_MODE)
            .toList();
        return usable.isEmpty() ? AnimationMode.GROUND_UP : usable.get(Math.floorMod(mix32(animationSeed), usable.size()));
    }

    public static double score(VisualBlock block, BoundingBox bounds, AnimationMode mode, long seed) {
        return score(block.position(), bounds, mode, seed);
    }

    public static double score(BlockPos pos, BoundingBox bounds, AnimationMode mode, long seed) {
        return score(
            pos.getX(), pos.getY(), pos.getZ(),
            bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ(),
            mode, seed);
    }

    /** Pure-data overload used by integrations and tests that do not load Minecraft classes. */
    public static double score(
        int x, int y, int z,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        AnimationMode mode,
        long seed
    ) {
        return ManifestationScoreMath.score(x, y, z, minX, minY, minZ, maxX, maxY, maxZ, mode, seed);
    }

    private static int mix32(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return (int)value;
    }

}
