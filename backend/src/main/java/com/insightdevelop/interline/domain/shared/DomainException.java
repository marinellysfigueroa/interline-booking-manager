package com.insightdevelop.interline.domain.shared;

import java.util.Objects;

/**
 * Raíz <b>sellada</b> de las excepciones de dominio.
 *
 * <p>Al ser {@code sealed}, el mapper que las convierte en Problem Details (fase 2)
 * puede usar un {@code switch} exhaustivo: si mañana se añade un subtipo, el
 * compilador obliga a decidir su código HTTP.
 *
 * <p>Spring Boot: equivale a tu jerarquía de excepciones de negocio + un
 * {@code @RestControllerAdvice}; en Quarkus se usa un {@code ExceptionMapper} JAX-RS
 * o {@code @ServerExceptionMapper}.
 */
public abstract sealed class DomainException extends RuntimeException
        permits BusinessRuleViolationException,
                InvalidStateTransitionException,
                ResourceNotFoundException,
                ExternalServiceUnavailableException {

    private final DomainErrorCode code;

    protected DomainException(DomainErrorCode code, String message) {
        this(code, message, null);
    }

    protected DomainException(DomainErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public DomainErrorCode code() {
        return code;
    }
}
