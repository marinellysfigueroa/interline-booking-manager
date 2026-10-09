package com.insightdevelop.interline.domain.airport;

import java.time.ZoneId;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Aeropuerto del catálogo.
 *
 * @param timeZone zona horaria del aeropuerto; las horas de los vuelos son locales a
 *                 cada aeropuerto y se necesita para calcular duraciones reales
 */
public record Airport(AirportCode code, String name, String city, String countryCode, ZoneId timeZone) {

    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");

    public Airport {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(countryCode, "countryCode");
        Objects.requireNonNull(timeZone, "timeZone");
        if (!COUNTRY.matcher(countryCode).matches()) {
            throw new IllegalArgumentException("Código de país ISO 3166-1 inválido: " + countryCode);
        }
    }
}
