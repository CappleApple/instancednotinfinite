package com.cappleapple.instancednotinfinite.manifestation;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class ManifestationStateMachine {
    private static final Map<ManifestationState, EnumSet<ManifestationState>> TRANSITIONS = new EnumMap<>(ManifestationState.class);

    static {
        allow(ManifestationState.PREPARING, ManifestationState.GENERATING, ManifestationState.FAILED, ManifestationState.CANCELLED);
        allow(ManifestationState.GENERATING, ManifestationState.MANIFESTING, ManifestationState.FINALIZING, ManifestationState.FAILED, ManifestationState.CANCELLED);
        allow(ManifestationState.MANIFESTING, ManifestationState.FINALIZING, ManifestationState.FAILED, ManifestationState.CANCELLED);
        allow(ManifestationState.FINALIZING, ManifestationState.COLLAPSING, ManifestationState.FAILED, ManifestationState.CANCELLED);
        allow(ManifestationState.COLLAPSING, ManifestationState.PORTAL_OPENING, ManifestationState.FAILED, ManifestationState.CANCELLED);
        allow(ManifestationState.PORTAL_OPENING, ManifestationState.PORTAL_OPEN, ManifestationState.CLOSING, ManifestationState.FAILED, ManifestationState.CANCELLED);
        allow(ManifestationState.PORTAL_OPEN, ManifestationState.CLOSING, ManifestationState.FAILED, ManifestationState.CANCELLED);
        allow(ManifestationState.CLOSING, ManifestationState.COMPLETE, ManifestationState.FAILED, ManifestationState.CANCELLED);
        allow(ManifestationState.COMPLETE);
        allow(ManifestationState.FAILED);
        allow(ManifestationState.CANCELLED);
    }

    private ManifestationStateMachine() {
    }

    public static boolean canTransition(ManifestationState from, ManifestationState to) {
        return TRANSITIONS.get(from).contains(to);
    }

    public static void requireTransition(ManifestationState from, ManifestationState to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Invalid manifestation transition " + from + " -> " + to);
        }
    }

    private static void allow(ManifestationState from, ManifestationState... to) {
        TRANSITIONS.put(from, to.length == 0 ? EnumSet.noneOf(ManifestationState.class) : EnumSet.of(to[0], to));
    }
}
