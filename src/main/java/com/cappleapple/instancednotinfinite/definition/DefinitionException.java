package com.cappleapple.instancednotinfinite.definition;

public final class DefinitionException extends Exception {
    private final String definitionId;
    private final String field;

    public DefinitionException(String definitionId, String field, String message) {
        super(message);
        this.definitionId = definitionId;
        this.field = field;
    }

    public DefinitionException(String definitionId, String field, String message, Throwable cause) {
        super(message, cause);
        this.definitionId = definitionId;
        this.field = field;
    }

    public String definitionId() {
        return definitionId;
    }

    public String field() {
        return field;
    }
}
