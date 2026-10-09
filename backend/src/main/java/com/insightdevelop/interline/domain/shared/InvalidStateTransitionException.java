package com.insightdevelop.interline.domain.shared;

/** La operación no es válida en el estado actual de la entidad. Se traduce a HTTP 409. */
public final class InvalidStateTransitionException extends DomainException {

    private final String currentState;
    private final String targetState;

    public InvalidStateTransitionException(String entity, Enum<?> current, Enum<?> target) {
        super(DomainErrorCode.INVALID_STATE_TRANSITION,
                "%s: no se puede pasar de %s a %s".formatted(entity, current, target));
        this.currentState = current.name();
        this.targetState = target.name();
    }

    public String currentState() {
        return currentState;
    }

    public String targetState() {
        return targetState;
    }
}
