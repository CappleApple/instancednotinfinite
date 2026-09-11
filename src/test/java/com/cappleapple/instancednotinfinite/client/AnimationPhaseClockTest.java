package com.cappleapple.instancednotinfinite.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.cappleapple.instancednotinfinite.manifestation.ManifestationState;
import org.junit.jupiter.api.Test;

class AnimationPhaseClockTest {
    @Test
    void repeatedPacketsAndWorldTimeCorrectionsCannotRewindPhase() {
        AnimationPhaseClock phase = new AnimationPhaseClock();
        phase.sync(ManifestationState.PORTAL_OPENING, 1_000L, 1_000L, 50.0);
        assertEquals(0.25F, phase.progress(55.0, 20));
        phase.sync(ManifestationState.PORTAL_OPENING, 1_000L, 990L, 55.0);
        assertEquals(0.5F, phase.progress(60.0, 20));
        phase.sync(ManifestationState.PORTAL_OPENING, 1_000L, 1_100L, 60.0);
        assertEquals(0.75F, phase.progress(65.0, 20));
    }

    @Test
    void joiningMidPhasePreservesAge() {
        AnimationPhaseClock phase = new AnimationPhaseClock();
        phase.sync(ManifestationState.COLLAPSING, 1_000L, 1_010L, 5.0);
        assertEquals(0.5F, phase.progress(5.0, 20));
        assertEquals(0.75F, phase.progress(10.0, 20));
        assertEquals(1.0F, phase.progress(100.0, 20));
    }

    @Test
    void newServerConfirmedPhaseOrRestartGetsNewAnchor() {
        AnimationPhaseClock phase = new AnimationPhaseClock();
        phase.sync(ManifestationState.COLLAPSING, 1_000L, 1_000L, 50.0);
        assertEquals(1.0F, phase.progress(70.0, 20));
        phase.sync(ManifestationState.PORTAL_OPENING, 1_020L, 1_020L, 70.0);
        assertEquals(0.25F, phase.progress(75.0, 20));
        phase.sync(ManifestationState.PORTAL_OPENING, 1_030L, 1_030L, 80.0);
        assertEquals(0.0F, phase.progress(80.0, 20));
    }

    @Test
    void phaseNeverStartsInFutureOrDividesByZero() {
        AnimationPhaseClock phase = new AnimationPhaseClock();
        phase.sync(ManifestationState.CLOSING, 1_020L, 1_000L, 50.0);
        assertEquals(0.0F, phase.progress(50.0, 0));
        assertEquals(1.0F, phase.progress(51.0, 0));
    }
}
