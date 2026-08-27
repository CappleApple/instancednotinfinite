package com.cappleapple.instancednotinfinite.config;

import java.util.List;

/** Shipping defaults kept dependency-free so release behavior can be asserted by the pure JVM suite. */
public final class ProductionConfigDefaults {
    public static final List<String> STRUCTURES = List.of();
    public static final boolean REGENERATE_RECIPE_CACHE = false;
    public static final int POOL_ITEM_SWAP_INTERVAL_SECONDS = 5;
    public static final String PORTAL_COMPLETION_OFFERING = "minecraft:blaze_powder";
    public static final int INSTANCE_OPEN_SECONDS = 300;
    public static final int INSTANCE_POST_VISIT_SECONDS = 60;
    public static final int INSTANCE_FORCE_COLLAPSE_SECONDS = -1;
    public static final double PORTAL_MINIMUM_WIDTH = 1.0D;
    public static final double PORTAL_MINIMUM_HEIGHT = 2.0D;
    public static final double PORTAL_DEPTH = 0.35D;
    public static final double PORTAL_MINIMUM_DEPTH = PORTAL_DEPTH;
    public static final boolean JADE_INTEGRATION = true;
    public static final boolean BUILT_IN_PORTAL_TOOLTIPS = true;

    private ProductionConfigDefaults() {
    }
}
