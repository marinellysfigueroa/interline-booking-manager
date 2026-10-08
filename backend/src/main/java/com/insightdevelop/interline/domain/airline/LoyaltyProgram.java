package com.insightdevelop.interline.domain.airline;

import java.util.Objects;

/** Programa de lealtad de una aerolínea (p. ej. "lifemiles", "Iberia Club"). */
public record LoyaltyProgram(String name) {

    public LoyaltyProgram {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("El nombre del programa de lealtad es obligatorio");
        }
    }
}
