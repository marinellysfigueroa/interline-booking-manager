package com.insightdevelop.interline.domain.shared;

/**
 * Petición de página, empezando en 0.
 *
 * <p>Spring Boot: equivale a {@code org.springframework.data.domain.PageRequest}; aquí
 * es un tipo propio para que el dominio no dependa de Panache ni de Spring Data.
 */
public record PageRequest(int page, int size) {

    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page debe ser >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size debe estar entre 1 y " + MAX_SIZE);
        }
    }

    public long offset() {
        return (long) page * size;
    }
}
