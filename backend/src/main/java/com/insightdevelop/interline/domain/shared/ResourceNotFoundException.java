package com.insightdevelop.interline.domain.shared;

/** El recurso solicitado no existe. Se traduce a HTTP 404. */
public final class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(DomainErrorCode code, String message) {
        super(code, message);
    }
}
