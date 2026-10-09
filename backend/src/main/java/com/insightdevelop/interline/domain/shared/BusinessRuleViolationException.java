package com.insightdevelop.interline.domain.shared;

/** Una regla de negocio impide la operación. Se traduce a HTTP 422. */
public final class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(DomainErrorCode code, String message) {
        super(code, message);
    }
}
