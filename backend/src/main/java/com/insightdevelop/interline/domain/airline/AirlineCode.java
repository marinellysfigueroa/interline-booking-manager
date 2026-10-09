package com.insightdevelop.interline.domain.airline;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Código IATA de aerolínea: 2 caracteres alfanuméricos que no pueden ser ambos dígitos (AV, IB, 4C...). */
public record AirlineCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^(?:[A-Z]{2}|[A-Z][0-9]|[0-9][A-Z])$");

    public AirlineCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Código IATA de aerolínea inválido: " + value);
        }
    }

    public static AirlineCode of(String raw) {
        return new AirlineCode(Objects.requireNonNull(raw, "raw").strip().toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
