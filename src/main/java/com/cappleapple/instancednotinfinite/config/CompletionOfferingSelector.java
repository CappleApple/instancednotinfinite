package com.cappleapple.instancednotinfinite.config;

import java.util.Optional;
import java.util.regex.Pattern;

/** Minecraft-independent parser for an exact item ID or an item tag prefixed with #. */
public record CompletionOfferingSelector(boolean tag, String resourceId) {
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public static Optional<CompletionOfferingSelector> parse(String configured) {
        if (configured == null || configured.isBlank()) return Optional.empty();
        boolean tag = configured.startsWith("#");
        String resourceId = tag ? configured.substring(1) : configured;
        if (!RESOURCE_ID.matcher(resourceId).matches()) return Optional.empty();
        return Optional.of(new CompletionOfferingSelector(tag, resourceId));
    }
}
