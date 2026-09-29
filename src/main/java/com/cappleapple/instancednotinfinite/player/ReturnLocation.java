package com.cappleapple.instancednotinfinite.player;

import com.cappleapple.instancednotinfinite.instance.InstanceId;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.nbt.Tag;

public record ReturnLocation(
    InstanceId instanceId,
    ResourceLocation dimension,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    Optional<SubLevelReturn> subLevel,
    Optional<GameType> previousGameMode
) {
    public ReturnLocation(InstanceId instanceId, ResourceLocation dimension,
            double x, double y, double z, float yaw, float pitch, Optional<SubLevelReturn> subLevel) {
        this(instanceId, dimension, x, y, z, yaw, pitch, subLevel, Optional.empty());
    }

    public ReturnLocation withPreviousGameMode(GameType mode) {
        return new ReturnLocation(instanceId, dimension, x, y, z, yaw, pitch, subLevel, Optional.of(mode));
    }

    public ReturnLocation(InstanceId instanceId, ResourceLocation dimension,
            double x, double y, double z, float yaw, float pitch) {
        this(instanceId, dimension, x, y, z, yaw, pitch, Optional.empty());
    }

    public record SubLevelReturn(UUID id, Vec3 localPosition, float localYaw) { }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Instance", this.instanceId.value());
        tag.putString("Dimension", this.dimension.toString());
        tag.putDouble("X", this.x);
        tag.putDouble("Y", this.y);
        tag.putDouble("Z", this.z);
        tag.putFloat("Yaw", this.yaw);
        tag.putFloat("Pitch", this.pitch);
        previousGameMode.ifPresent(mode -> tag.putString("PreviousGameMode", mode.getName()));
        subLevel.ifPresent(anchor -> {
            CompoundTag sub = new CompoundTag();
            sub.putUUID("Id", anchor.id());
            sub.putDouble("X", anchor.localPosition().x);
            sub.putDouble("Y", anchor.localPosition().y);
            sub.putDouble("Z", anchor.localPosition().z);
            sub.putFloat("Yaw", anchor.localYaw());
            tag.put("SubLevel", sub);
        });
        return tag;
    }

    static ReturnLocation load(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null) {
            throw new IllegalArgumentException("Invalid return dimension " + tag.getString("Dimension"));
        }
        CompoundTag sub = tag.getCompound("SubLevel");
        return new ReturnLocation(
            new InstanceId(tag.getUUID("Instance")), dimension,
            tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
            tag.getFloat("Yaw"), tag.getFloat("Pitch"),
            sub.hasUUID("Id") ? Optional.of(new SubLevelReturn(sub.getUUID("Id"),
                new Vec3(sub.getDouble("X"), sub.getDouble("Y"), sub.getDouble("Z")), sub.getFloat("Yaw")))
                : Optional.empty(),
            tag.contains("PreviousGameMode", Tag.TAG_STRING)
                ? Optional.ofNullable(GameType.byName(tag.getString("PreviousGameMode"), null)) : Optional.empty());
    }
}
