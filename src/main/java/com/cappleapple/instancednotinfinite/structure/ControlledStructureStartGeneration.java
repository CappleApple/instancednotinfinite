package com.cappleapple.instancednotinfinite.structure;

/**
 * Marks an explicit INI structure-start probe. Optional compatibility mixins use this narrow
 * scope to skip natural-world location filters that are not relevant inside a controlled instance.
 */
public final class ControlledStructureStartGeneration {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private ControlledStructureStartGeneration() {
    }

    public static Scope begin() {
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("Nested controlled structure-start generation");
        }
        ACTIVE.set(Boolean.TRUE);
        return new Scope();
    }

    public static boolean isActive() {
        return ACTIVE.get() != null;
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() {
        }

        @Override
        public void close() {
            if (this.closed) return;
            this.closed = true;
            ACTIVE.remove();
        }
    }
}
