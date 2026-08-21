package com.cappleapple.instancednotinfinite.cleanup;

import java.nio.file.Path;

public final class CleanupPathGuard {
    private CleanupPathGuard() {
    }

    public static Path requireOwnedTarget(Path expectedRoot, Path target) {
        Path root = expectedRoot.toAbsolutePath().normalize();
        Path normalized = target.toAbsolutePath().normalize();
        if (normalized.equals(root) || !normalized.startsWith(root) || !root.equals(normalized.getParent())) {
            throw new SecurityException("Refusing cleanup outside the dedicated instance root: " + normalized);
        }
        String name = normalized.getFileName().toString();
        if (!name.matches("[0-9a-f]{32}")) {
            throw new SecurityException("Refusing cleanup of a non-UUID instance directory: " + normalized);
        }
        return normalized;
    }
}
