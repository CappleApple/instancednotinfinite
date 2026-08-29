package com.cappleapple.instancednotinfinite.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ServerConfigDefaultsTest {
    @Test
    void productionCatalogueStartsEmpty() {
        assertEquals(List.of(), ProductionConfigDefaults.STRUCTURES);
    }

    @Test
    void forcedCollapseIsDisabledByDefault() {
        assertEquals(300, ProductionConfigDefaults.INSTANCE_OPEN_SECONDS);
        assertEquals(60, ProductionConfigDefaults.INSTANCE_POST_VISIT_SECONDS);
        assertEquals(-1, ProductionConfigDefaults.INSTANCE_FORCE_COLLAPSE_SECONDS);
    }

    @Test
    void countdownFloorIsOneByTwoBlocks() {
        assertEquals(1.0D, ProductionConfigDefaults.PORTAL_MINIMUM_WIDTH);
        assertEquals(2.0D, ProductionConfigDefaults.PORTAL_MINIMUM_HEIGHT);
        assertEquals(ProductionConfigDefaults.PORTAL_DEPTH, ProductionConfigDefaults.PORTAL_MINIMUM_DEPTH);
    }

    @Test
    void poolCatalystsSwapEveryFiveSecondsByDefault() {
        assertEquals(5, ProductionConfigDefaults.POOL_ITEM_SWAP_INTERVAL_SECONDS);
    }

    @Test
    void recipeCacheRegenerationIsOneShotOptIn() {
        assertEquals(false, ProductionConfigDefaults.REGENERATE_RECIPE_CACHE);
    }

    @Test
    void blazePowderCompletesPortalsByDefault() {
        assertEquals("minecraft:blaze_powder", ProductionConfigDefaults.PORTAL_COMPLETION_OFFERING);
    }

    @Test
    void portalPresentationUsesThemedVanillaSoundsByDefault() {
        assertEquals("minecraft:block.beacon.ambient", ProductionConfigDefaults.GENERATION_SOUND);
        assertEquals("minecraft:block.beacon.activate", ProductionConfigDefaults.PORTAL_OPEN_SOUND);
        assertEquals("minecraft:block.trial_spawner.ambient_ominous", ProductionConfigDefaults.PORTAL_AMBIENT_SOUND);
        assertEquals("minecraft:block.conduit.activate", ProductionConfigDefaults.PORTAL_WALK_THROUGH_SOUND);
        assertEquals("minecraft:block.beacon.deactivate", ProductionConfigDefaults.PORTAL_CLOSING_SOUND);
        assertEquals("minecraft:block.fire.extinguish", ProductionConfigDefaults.PORTAL_CLOSED_SOUND);
    }

    @Test
    void portalSoundVolumesUseCalibratedDefaults() {
        assertEquals(0.25D, ProductionConfigDefaults.GENERATION_SOUND_VOLUME);
        assertEquals(0.4D, ProductionConfigDefaults.PORTAL_OPEN_SOUND_VOLUME);
        assertEquals(0.1D, ProductionConfigDefaults.PORTAL_AMBIENT_SOUND_VOLUME);
        assertEquals(1.0D, ProductionConfigDefaults.PORTAL_WALK_THROUGH_SOUND_VOLUME);
        assertEquals(0.25D, ProductionConfigDefaults.PORTAL_CLOSING_SOUND_VOLUME);
        assertEquals(0.25D, ProductionConfigDefaults.PORTAL_CLOSED_SOUND_VOLUME);
    }

    @Test
    void portalTooltipIntegrationsAreAvailableByDefault() {
        assertEquals(true, ProductionConfigDefaults.JADE_INTEGRATION);
        assertEquals(true, ProductionConfigDefaults.BUILT_IN_PORTAL_TOOLTIPS);
    }
}
