package com.cappleapple.instancednotinfinite.api;

import com.cappleapple.instancednotinfinite.instance.InstanceOperationException;
import com.cappleapple.instancednotinfinite.manifestation.DungeonManifestationManager;
import com.cappleapple.instancednotinfinite.manifestation.DungeonTarget;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationOptions;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Stable trigger-independent entry point for rituals, quests, bosses, scripting adapters, and commands. */
public final class DungeonManifestationApi {
    private DungeonManifestationApi() {
    }

    public static ManifestationView spawn(
        ServerLevel level,
        BlockPos origin,
        DungeonTarget target,
        ManifestationOptions options,
        @Nullable ServerPlayer initiator
    ) throws InstanceOperationException {
        return ManifestationView.from(
            DungeonManifestationManager.get(level.getServer()).spawn(level, origin, target, options, initiator));
    }

    public static void cancel(ServerLevel level, UUID manifestationId) throws InstanceOperationException {
        DungeonManifestationManager.get(level.getServer()).cancel(manifestationId, "Cancelled through public API");
    }

    public static Optional<ManifestationView> get(ServerLevel level, UUID manifestationId) {
        return DungeonManifestationManager.get(level.getServer()).get(manifestationId).map(ManifestationView::from);
    }

    public static Optional<ManifestationView> getAt(ServerLevel level, BlockPos origin) {
        return DungeonManifestationManager.get(level.getServer()).getAt(level, origin).map(ManifestationView::from);
    }
}
