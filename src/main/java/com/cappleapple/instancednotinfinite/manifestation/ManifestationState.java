package com.cappleapple.instancednotinfinite.manifestation;

public enum ManifestationState {
    PREPARING,
    GENERATING,
    MANIFESTING,
    FINALIZING,
    COLLAPSING,
    PORTAL_OPENING,
    PORTAL_OPEN,
    CLOSING,
    COMPLETE,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == COMPLETE || this == FAILED || this == CANCELLED;
    }
}
