package com.insightdevelop.interline.infrastructure.provider.amadeus;

/**
 * Fallo HTTP del proveedor, clasificado para las políticas de Fault Tolerance:
 * {@link Transient} se reintenta y cuenta para el circuit breaker; {@link Rejected} (4xx) no
 * se reintenta ni abre el circuito (es un error nuestro, no una caída del proveedor);
 * {@link Unauthorized} provoca renovar el token.
 */
public sealed class ProviderCallException extends RuntimeException
        permits ProviderCallException.Transient, ProviderCallException.Rejected, ProviderCallException.Unauthorized {

    private final int status;

    ProviderCallException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static final class Transient extends ProviderCallException {
        public Transient(int status, String message) {
            super(status, message);
        }
    }

    public static final class Rejected extends ProviderCallException {
        public Rejected(int status, String message) {
            super(status, message);
        }
    }

    public static final class Unauthorized extends ProviderCallException {
        public Unauthorized(String message) {
            super(401, message);
        }
    }
}
