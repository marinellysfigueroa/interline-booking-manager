package com.insightdevelop.interline.domain.airport;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Código IATA de aeropuerto (3 letras): BOG, MAD, FCO, MIA, LIM... */
public record AirportCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z]{3}$");

    public AirportCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Código IATA de aeropuerto inválido: " + value);
        }
    }

    public static AirportCode of(String raw) {
        return new AirportCode(Objects.requireNonNull(raw, "raw").strip().toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
