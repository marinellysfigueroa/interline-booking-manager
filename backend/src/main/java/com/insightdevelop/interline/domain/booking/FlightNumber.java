package com.insightdevelop.interline.domain.booking;

import java.util.Objects;
import java.util.regex.Pattern;

/** Número de vuelo sin el código de aerolínea: 1–4 dígitos y un sufijo opcional (26, 3234, 120A). */
public record FlightNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[0-9]{1,4}[A-Z]?$");

    public FlightNumber {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Número de vuelo inválido: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
