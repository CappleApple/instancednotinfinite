package com.cappleapple.instancednotinfinite.player;

import com.cappleapple.instancednotinfinite.InstancedNotInfinite;
import com.cappleapple.instancednotinfinite.config.ServerConfig;
import com.cappleapple.instancednotinfinite.compat.SableCoordinates;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
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
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(player);
        Vec3 world = SableCoordinates.toWorld(player.level(), player.position());
        Vec3 local = subLevel == null ? world : subLevel.logicalPose().transformPositionInverse(world);
        capture(player, instance, local, subLevel);
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
        Vec3 localPlayer = SableCoordinates.toLocal(player.level(), portalPos, player.position());
        double sideDistance = (localPlayer.x - centerX) * normalX + (localPlayer.z - centerZ) * normalZ;
        double side = sideDistance < -1.0E-6 ? -1.0 : 1.0;
        capture(player, instance,
            new Vec3(centerX + normalX * side * offsetBlocks, portalPos.getY(), centerZ + normalZ * side * offsetBlocks),
            SableCoordinates.subLevel(player.level(), portalPos));
    }

    private void capture(ServerPlayer player, DungeonInstance instance, Vec3 local, SubLevelAccess subLevel) {
        Vec3 world = subLevel == null ? local : subLevel.logicalPose().transformPosition(local);
        Optional<ReturnLocation.SubLevelReturn> anchor = subLevel == null ? Optional.empty()
            : Optional.of(new ReturnLocation.SubLevelReturn(subLevel.getUniqueId(), local,
                SableCoordinates.yaw(subLevel.logicalPose().transformNormalInverse(
                    Vec3.directionFromRotation(0, player.getYRot())))));
        PlayerReturnSavedData.get(player.getServer()).putIfAbsent(player.getUUID(),
            new ReturnLocation(instance.id(), player.level().dimension().location(),
                world.x, world.y, world.z, player.getYRot(), player.getXRot(), anchor));
    }

    public boolean returnPlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        PlayerReturnSavedData data = PlayerReturnSavedData.get(server);
        ReturnLocation location = data.get(player.getUUID()).orElse(null);
        if (location == null) {
            return false;
        }

        Destination destination = destination(player, location);
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(destination.level(), destination.position().x, destination.position().y,
            destination.position().z, destination.yaw(), destination.pitch());
        data.remove(player.getUUID());
        InstancedNotInfinite.LOGGER.info("Returned player {} from dungeon instance {}", player.getGameProfile().getName(), location.instanceId().shortId());
        return true;
    }

    /** Rewrite saved instance coordinates before vanilla selects a level or loads any player position. */
    public static void recoverBeforeLoad(ServerPlayer player, CompoundTag tag) {
        ResourceLocation savedDimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (!isInstanceDimension(savedDimension)) return;
        ReturnLocation location = PlayerReturnSavedData.get(player.getServer()).get(player.getUUID()).orElse(null);
        Destination destination = destination(player, location);
        tag.putString("Dimension", destination.level().dimension().location().toString());
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(destination.position().x));
        position.add(DoubleTag.valueOf(destination.position().y));
        position.add(DoubleTag.valueOf(destination.position().z));
        tag.put("Pos", position);
        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(destination.yaw()));
        rotation.add(FloatTag.valueOf(destination.pitch()));
        tag.put("Rotation", rotation);
        ListTag motion = new ListTag();
        for (int axis = 0; axis < 3; axis++) motion.add(DoubleTag.valueOf(0));
        tag.put("Motion", motion);
        tag.putFloat("FallDistance", 0);
        tag.putInt("PortalCooldown", 100);
        tag.remove("RootVehicle");
        // Sable 2.0.5 restores this point inside Entity.load, before PlayerLoggedInEvent.
        tag.remove("LoginPoint");
        player.setServerLevel(destination.level());
        InstancedNotInfinite.LOGGER.info("Recovered login for {} from {} before loading the player position",
            player.getGameProfile().getName(), savedDimension);
        // Keep the saved return until login succeeds; the logged-in handler acknowledges it.
    }

    public static boolean isInstanceDimension(ResourceLocation dimension) {
        return dimension != null && dimension.getNamespace().equals(InstancedNotInfinite.MOD_ID)
            && dimension.getPath().startsWith("instances/");
    }

    private static Destination destination(ServerPlayer player, ReturnLocation location) {
        MinecraftServer server = player.getServer();
        if (location != null && !isInstanceDimension(location.dimension())) {
            ServerLevel target = server.getLevel(ResourceKey.create(Registries.DIMENSION, location.dimension()));
            Vec3 position = new Vec3(location.x(), location.y(), location.z());
            float yaw = location.yaw();
            boolean available = target != null;
            if (available && location.subLevel().isPresent()) {
                ReturnLocation.SubLevelReturn anchor = location.subLevel().orElseThrow();
                SubLevelAccess subLevel = SableCoordinates.subLevel(target, BlockPos.containing(anchor.localPosition()));
                available = subLevel != null && subLevel.getUniqueId().equals(anchor.id());
                if (available) {
                    position = subLevel.logicalPose().transformPosition(anchor.localPosition());
                    yaw = SableCoordinates.yaw(subLevel.logicalPose().transformNormal(
                        Vec3.directionFromRotation(0, anchor.localYaw())));
                }
            }
            // Old records may contain untransformed plot coordinates. Never load a plotyard as terrain.
            if (available && Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z)
                && Float.isFinite(yaw) && Float.isFinite(location.pitch())
                && !SableCompanion.INSTANCE.isInPlotGrid(target, position)
                && target.getWorldBorder().isWithinBounds(BlockPos.containing(position))
                && position.y >= target.getMinBuildHeight() && position.y + 2 < target.getMaxBuildHeight()
                && target.noCollision(player, player.getBoundingBox().move(position.subtract(player.position())))) {
                return new Destination(target, position, yaw, location.pitch());
            }
        }
        ServerLevel target = fallback(server);
        return new Destination(target, Vec3.atBottomCenterOf(target.getSharedSpawnPos()).add(0, 1, 0),
            target.getSharedSpawnAngle(), 0);
    }

    private record Destination(ServerLevel level, Vec3 position, float yaw, float pitch) { }

    public void recoverOnLogin(ServerPlayer player) {
        PlayerReturnSavedData data = PlayerReturnSavedData.get(player.getServer());
        if (data.get(player.getUUID()).isEmpty()) {
            return;
        }
        if (isInstanceDimension(player.level().dimension().location())) {
            returnPlayer(player);
        } else {
            // The prior return may have completed just before a crash; normal-world placement wins.
            data.remove(player.getUUID());
        }
    }

    public void returnFromVoid(ServerPlayer player) {
        player.fallDistance = 0.0F;
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (!returnPlayer(player)) {
            // Administrative teleports may bypass entrance capture; never leave that player in the void.
            ServerLevel target = fallback(player.getServer());
            BlockPos spawn = target.getSharedSpawnPos();
            player.teleportTo(target, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5,
                target.getSharedSpawnAngle(), 0.0F);
        }
        player.fallDistance = 0.0F;
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.setPortalCooldown();
    }

    private static ServerLevel fallback(MinecraftServer server) {
        ResourceLocation configured = ResourceLocation.tryParse(ServerConfig.INSTANCE.fallbackReturnDimension.get());
        if (configured != null) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, configured));
            if (level != null && !isInstanceDimension(configured)) {
                return level;
            }
        }
        return server.overworld();
    }

}
