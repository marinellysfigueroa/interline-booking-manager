package com.insightdevelop.interline.domain.booking;

import java.util.Objects;
import java.util.regex.Pattern;

/** Número de ticket de 13 dígitos: prefijo contable de la validadora (3) + número de serie (10). */
public record TicketNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[0-9]{13}$");

    public TicketNumber {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Número de ticket inválido: " + value);
        }
    }

    public static TicketNumber of(String accountingCode, long serial) {
        if (serial < 0 || serial > 9_999_999_999L) {
            throw new IllegalArgumentException("El número de serie debe tener como máximo 10 dígitos: " + serial);
        }
        return new TicketNumber(accountingCode + "%010d".formatted(serial));
    }

    public String accountingCode() {
        return value.substring(0, 3);
    }
}
