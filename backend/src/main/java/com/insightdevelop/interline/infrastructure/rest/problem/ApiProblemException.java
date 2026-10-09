package com.insightdevelop.interline.infrastructure.rest.problem;

/** Error propio del adaptador HTTP (p. ej. idempotencia) con su estado y código. */
public class ApiProblemException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiProblemException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
