package com.cappleapple.instancednotinfinite.instance;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class InstanceStateMachine {
    private static final Map<InstanceState, Set<InstanceState>> ALLOWED = buildTransitions();

    private InstanceStateMachine() {
    }

    public static boolean canTransition(InstanceState from, InstanceState to) {
        return from == to || ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(InstanceState from, InstanceState to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Illegal dungeon instance transition " + from + " -> " + to);
        }
    }

    private static Map<InstanceState, Set<InstanceState>> buildTransitions() {
        EnumMap<InstanceState, Set<InstanceState>> transitions = new EnumMap<>(InstanceState.class);
        transitions.put(InstanceState.CREATING, EnumSet.of(InstanceState.ACTIVE, InstanceState.FAILED));
        transitions.put(InstanceState.ACTIVE, EnumSet.of(InstanceState.VACANT, InstanceState.COMPLETED, InstanceState.UNLOADING, InstanceState.FAILED));
        transitions.put(InstanceState.VACANT, EnumSet.of(InstanceState.ACTIVE, InstanceState.COMPLETED, InstanceState.UNLOADING, InstanceState.FAILED));
        transitions.put(InstanceState.COMPLETED, EnumSet.of(InstanceState.UNLOADING, InstanceState.FAILED));
        transitions.put(InstanceState.UNLOADING, EnumSet.of(InstanceState.DELETE_PENDING, InstanceState.FAILED));
        transitions.put(InstanceState.DELETE_PENDING, EnumSet.of(InstanceState.DELETED, InstanceState.FAILED));
        transitions.put(InstanceState.FAILED, EnumSet.of(InstanceState.UNLOADING, InstanceState.DELETE_PENDING, InstanceState.DELETED));
        transitions.put(InstanceState.DELETED, EnumSet.noneOf(InstanceState.class));
        return Map.copyOf(transitions);
    }
}
