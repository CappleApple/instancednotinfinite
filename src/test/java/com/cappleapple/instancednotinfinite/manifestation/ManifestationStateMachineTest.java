package com.cappleapple.instancednotinfinite.manifestation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ManifestationStateMachineTest {
    @Test
    void validLifecycleReachesPortalAndCompletion() {
        assertTrue(ManifestationStateMachine.canTransition(ManifestationState.PREPARING, ManifestationState.GENERATING));
        assertTrue(ManifestationStateMachine.canTransition(ManifestationState.GENERATING, ManifestationState.FINALIZING));
        assertTrue(ManifestationStateMachine.canTransition(ManifestationState.FINALIZING, ManifestationState.COLLAPSING));
        assertTrue(ManifestationStateMachine.canTransition(ManifestationState.COLLAPSING, ManifestationState.PORTAL_OPENING));
        assertTrue(ManifestationStateMachine.canTransition(ManifestationState.PORTAL_OPENING, ManifestationState.PORTAL_OPEN));
        assertFalse(ManifestationStateMachine.canTransition(ManifestationState.COLLAPSING, ManifestationState.PORTAL_OPEN));
        assertTrue(ManifestationStateMachine.canTransition(ManifestationState.PORTAL_OPEN, ManifestationState.CLOSING));
        assertTrue(ManifestationStateMachine.canTransition(ManifestationState.CLOSING, ManifestationState.COMPLETE));
        assertTrue(ManifestationStateMachine.canTransition(ManifestationState.CLOSING, ManifestationState.CANCELLED));
    }

    @Test
    void portalCannotOpenBeforeFinalization() {
        assertFalse(ManifestationStateMachine.canTransition(ManifestationState.GENERATING, ManifestationState.PORTAL_OPEN));
        assertThrows(IllegalStateException.class,
            () -> ManifestationStateMachine.requireTransition(ManifestationState.GENERATING, ManifestationState.PORTAL_OPEN));
    }
}
