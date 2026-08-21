package com.cappleapple.instancednotinfinite.instance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceStateMachineTest {
    @Test
    void acceptsSuccessfulLifecycle() {
        assertDoesNotThrow(() -> {
            InstanceStateMachine.requireTransition(InstanceState.CREATING, InstanceState.ACTIVE);
            InstanceStateMachine.requireTransition(InstanceState.ACTIVE, InstanceState.COMPLETED);
            InstanceStateMachine.requireTransition(InstanceState.COMPLETED, InstanceState.UNLOADING);
            InstanceStateMachine.requireTransition(InstanceState.UNLOADING, InstanceState.DELETE_PENDING);
            InstanceStateMachine.requireTransition(InstanceState.DELETE_PENDING, InstanceState.DELETED);
        });
    }

    @Test
    void vacantInstanceCanBeReactivated() {
        assertTrue(InstanceStateMachine.canTransition(InstanceState.ACTIVE, InstanceState.VACANT));
        assertTrue(InstanceStateMachine.canTransition(InstanceState.VACANT, InstanceState.ACTIVE));
    }

    @Test
    void deletedInstanceCannotBecomeActive() {
        assertFalse(InstanceStateMachine.canTransition(InstanceState.DELETED, InstanceState.ACTIVE));
        assertThrows(IllegalStateException.class,
            () -> InstanceStateMachine.requireTransition(InstanceState.DELETED, InstanceState.ACTIVE));
    }
}
