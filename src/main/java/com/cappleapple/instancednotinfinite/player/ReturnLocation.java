package com.cappleapple.instancednotinfinite.player;

import com.cappleapple.instancednotinfinite.instance.InstanceId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record ReturnLocation(
    InstanceId instanceId,
    ResourceLocation dimension,
    double x,
    double y,
    double z,
    float yaw,
    float pitch
) {
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Instance", this.instanceId.value());
        tag.putString("Dimension", this.dimension.toString());
        tag.putDouble("X", this.x);
        tag.putDouble("Y", this.y);
        tag.putDouble("Z", this.z);
        tag.putFloat("Yaw", this.yaw);
        tag.putFloat("Pitch", this.pitch);
        return tag;
    }

    static ReturnLocation load(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null) {
            throw new IllegalArgumentException("Invalid return dimension " + tag.getString("Dimension"));
        }
        return new ReturnLocation(
            new InstanceId(tag.getUUID("Instance")), dimension,
            tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
            tag.getFloat("Yaw"), tag.getFloat("Pitch"));
    }
}
