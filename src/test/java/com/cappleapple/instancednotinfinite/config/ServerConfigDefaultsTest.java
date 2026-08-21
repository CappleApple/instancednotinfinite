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
    void blazePowderCompletesPortalsByDefault() {
        assertEquals("minecraft:blaze_powder", ProductionConfigDefaults.PORTAL_COMPLETION_OFFERING);
    }

    @Test
    void portalTooltipIntegrationsAreAvailableByDefault() {
        assertEquals(true, ProductionConfigDefaults.JADE_INTEGRATION);
        assertEquals(true, ProductionConfigDefaults.BUILT_IN_PORTAL_TOOLTIPS);
    }
}
