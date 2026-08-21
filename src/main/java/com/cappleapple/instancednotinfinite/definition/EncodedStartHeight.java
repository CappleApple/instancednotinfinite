package com.cappleapple.instancednotinfinite.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.OptionalInt;

/** Pure parser for the start-height fragment emitted by structure codecs. */
final class EncodedStartHeight {
    private EncodedStartHeight() {
    }

    static OptionalInt absolute(JsonElement encoded) {
        if (encoded == null || !encoded.isJsonObject()) {
            return OptionalInt.empty();
        }
        JsonElement startHeight = encoded.getAsJsonObject().get("start_height");
        if (startHeight == null || !startHeight.isJsonObject()) {
            return OptionalInt.empty();
        }
        JsonObject heightObject = startHeight.getAsJsonObject();
        OptionalInt direct = absoluteAnchor(heightObject);
        if (direct.isPresent()) {
            return direct;
        }

        OptionalInt minimum = absoluteAnchor(heightObject.get("min_inclusive"));
        OptionalInt maximum = absoluteAnchor(heightObject.get("max_inclusive"));
        if (minimum.isPresent() && maximum.isPresent() && minimum.getAsInt() == maximum.getAsInt()) {
            return minimum;
        }
        return OptionalInt.empty();
    }

    private static OptionalInt absoluteAnchor(JsonElement encoded) {
        if (encoded == null || !encoded.isJsonObject()) {
            return OptionalInt.empty();
        }
        JsonElement absolute = encoded.getAsJsonObject().get("absolute");
        if (absolute == null || !absolute.isJsonPrimitive() || !absolute.getAsJsonPrimitive().isNumber()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(absolute.getAsInt());
    }
}
