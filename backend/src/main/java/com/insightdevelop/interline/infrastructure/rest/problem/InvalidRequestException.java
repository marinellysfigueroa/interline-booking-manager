package com.insightdevelop.interline.infrastructure.rest.problem;

/**
 * La petición no cumple una regla del contrato que Bean Validation no puede expresar
 * (p. ej. la forma del pago según el método). Se traduce a 400.
 */
public class InvalidRequestException extends RuntimeException {

    private final String field;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
