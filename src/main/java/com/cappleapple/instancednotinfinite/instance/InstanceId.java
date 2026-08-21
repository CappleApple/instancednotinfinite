package com.cappleapple.instancednotinfinite.instance;

import java.util.UUID;

public record InstanceId(UUID value) {
    public InstanceId {
        if (value == null) {
            throw new IllegalArgumentException("instance UUID must not be null");
        }
    }

    public static InstanceId random() {
        return new InstanceId(UUID.randomUUID());
    }

    public static InstanceId parse(String value) {
        return new InstanceId(UUID.fromString(value));
    }

    public String pathSegment() {
        return this.value.toString().replace("-", "");
    }

    public String shortId() {
        return this.value.toString().substring(0, 8);
    }

    @Override
    public String toString() {
        return this.value.toString();
    }
}
