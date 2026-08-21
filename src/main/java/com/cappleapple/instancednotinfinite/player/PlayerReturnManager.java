package com.cappleapple.instancednotinfinite.player;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.instance.DungeonInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class PlayerReturnManager {
    public void capture(ServerPlayer player, DungeonInstance instance) {
        PlayerReturnSavedData.get(player.getServer()).putIfAbsent(
            player.getUUID(),
            new ReturnLocation(
                instance.id(), player.level().dimension().location(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
    }

    public void captureFromPortal(
        ServerPlayer player,
        DungeonInstance instance,
        BlockPos portalPos,
        int portalRotationDegrees,
        int offsetBlocks
    ) {
        double centerX = portalPos.getX() + 0.5;
        double centerZ = portalPos.getZ() + 0.5;
        double normalX = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.normalX(portalRotationDegrees);
        double normalZ = com.cappleapple.instancednotinfinite.manifestation.PortalRotation.normalZ(portalRotationDegrees);
        double sideDistance = (player.getX() - centerX) * normalX + (player.getZ() - centerZ) * normalZ;
        double side = sideDistance < -1.0E-6 ? -1.0 : 1.0;
        PlayerReturnSavedData.get(player.getServer()).putIfAbsent(
            player.getUUID(),
            new ReturnLocation(
                instance.id(), player.level().dimension().location(),
                centerX + normalX * side * offsetBlocks, portalPos.getY(), centerZ + normalZ * side * offsetBlocks,
                player.getYRot(), player.getXRot()));
    }

    public boolean returnPlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        PlayerReturnSavedData data = PlayerReturnSavedData.get(server);
        ReturnLocation location = data.get(player.getUUID()).orElse(null);
        if (location == null) {
            return false;
        }

        ResourceKey<Level> requestedKey = ResourceKey.create(Registries.DIMENSION, location.dimension());
        ServerLevel target = server.getLevel(requestedKey);
        BlockPos requested = BlockPos.containing(location.x(), location.y(), location.z());
        boolean exact = target != null
            && target.getWorldBorder().isWithinBounds(requested)
            && requested.getY() >= target.getMinBuildHeight()
            && requested.getY() + 1 < target.getMaxBuildHeight()
            && target.noCollision(
                player,
                player.getBoundingBox().move(
                    location.x() - player.getX(),
                    location.y() - player.getY(),
                    location.z() - player.getZ()));
        if (!exact) {
            target = fallback(server);
        }

        if (exact) {
            player.teleportTo(target, location.x(), location.y(), location.z(), location.yaw(), location.pitch());
        } else {
            BlockPos spawn = target.getSharedSpawnPos();
            player.teleportTo(target, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5, target.getSharedSpawnAngle(), 0.0F);
        }
        data.remove(player.getUUID());
        InstancedNotInfinite.LOGGER.info("Returned player {} from dungeon instance {}", player.getGameProfile().getName(), location.instanceId().shortId());
        return true;
    }

    public void recoverOnLogin(ServerPlayer player) {
        PlayerReturnSavedData data = PlayerReturnSavedData.get(player.getServer());
        if (data.get(player.getUUID()).isEmpty()) {
            return;
        }
        if (player.level().dimension().location().getNamespace().equals(InstancedNotInfinite.MOD_ID)) {
            returnPlayer(player);
        } else {
            // The prior return may have completed just before a crash; normal-world placement wins.
            data.remove(player.getUUID());
        }
    }

    private static ServerLevel fallback(MinecraftServer server) {
        ResourceLocation configured = ResourceLocation.tryParse(ServerConfig.INSTANCE.fallbackReturnDimension.get());
        if (configured != null) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, configured));
            if (level != null) {
                return level;
            }
        }
        return server.overworld();
    }

}
