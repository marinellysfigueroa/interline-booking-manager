package com.insightdevelop.interline.domain.shared;

/**
 * Un sistema externo (proveedor de vuelos, inventario, pagos) no está disponible
 * tras agotar reintentos o con el circuit breaker abierto. Se traduce a HTTP 503.
 */
public final class ExternalServiceUnavailableException extends DomainException {

    public ExternalServiceUnavailableException(String message, Throwable cause) {
        super(DomainErrorCode.PROVIDER_UNAVAILABLE, message, cause);
    }
}
