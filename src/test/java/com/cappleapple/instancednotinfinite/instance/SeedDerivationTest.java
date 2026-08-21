package com.cappleapple.instancednotinfinite.instance;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SeedDerivationTest {
    private static final UUID ID = UUID.fromString("8c453581-0f54-44f7-a590-dde7c6cebc72");

    @Test
    void derivationIsDeterministic() {
        assertEquals(SeedDerivation.derive(42L, ID, "example:crypt"), SeedDerivation.derive(42L, ID, "example:crypt"));
    }

    @Test
    void eachInputInfluencesTheSeed() {
        long base = SeedDerivation.derive(42L, ID, "example:crypt");
        assertNotEquals(base, SeedDerivation.derive(43L, ID, "example:crypt"));
        assertNotEquals(base, SeedDerivation.derive(42L, UUID.randomUUID(), "example:crypt"));
        assertNotEquals(base, SeedDerivation.derive(42L, ID, "example:tower"));
    }
}
