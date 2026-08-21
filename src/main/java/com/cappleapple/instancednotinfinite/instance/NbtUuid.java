package com.cappleapple.instancednotinfinite.instance;

import java.util.UUID;

final class NbtUuid {
    private NbtUuid() {
    }

    static UUID uuidFromIntArray(int[] values) {
        if (values.length != 4) {
            throw new IllegalArgumentException("UUID int array must contain four integers");
        }
        long most = (long)values[0] << 32 | values[1] & 0xFFFFFFFFL;
        long least = (long)values[2] << 32 | values[3] & 0xFFFFFFFFL;
        return new UUID(most, least);
    }
}
