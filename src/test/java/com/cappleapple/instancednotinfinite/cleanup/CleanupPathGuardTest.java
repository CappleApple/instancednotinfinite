package com.cappleapple.instancednotinfinite.cleanup;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CleanupPathGuardTest {
    private static final Path ROOT = Path.of("build", "test-world", "dimensions", "instancednotinfinite", "instances").toAbsolutePath();

    @Test
    void acceptsOneCanonicalUuidChild() {
        Path child = ROOT.resolve("8c4535810f5444f7a590dde7c6cebc72");
        assertEquals(child.normalize(), CleanupPathGuard.requireOwnedTarget(ROOT, child));
    }

    @Test
    void rejectsRootTraversalAndNestedTargets() {
        assertThrows(SecurityException.class, () -> CleanupPathGuard.requireOwnedTarget(ROOT, ROOT));
        assertThrows(SecurityException.class, () -> CleanupPathGuard.requireOwnedTarget(ROOT, ROOT.resolve("..").resolve("world")));
        assertThrows(SecurityException.class,
            () -> CleanupPathGuard.requireOwnedTarget(ROOT, ROOT.resolve("8c4535810f5444f7a590dde7c6cebc72").resolve("region")));
    }

    @Test
    void rejectsUntrustedDirectoryNames() {
        assertThrows(SecurityException.class, () -> CleanupPathGuard.requireOwnedTarget(ROOT, ROOT.resolve("../../overworld")));
        assertThrows(SecurityException.class, () -> CleanupPathGuard.requireOwnedTarget(ROOT, ROOT.resolve("not-a-uuid")));
    }
}
