package com.insightdevelop.interline.domain.booking;

import java.util.Locale;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Localizador tipo PNR de 6 caracteres.
 *
 * <p>El alfabeto excluye 0, 1, I y O para que el localizador pueda dictarse por
 * teléfono sin ambigüedad: 32 símbolos → 32^6 ≈ 1.070 millones de combinaciones.
 * Las colisiones se resuelven reintentando en la capa de aplicación (índice único).
 */
public record BookingLocator(String value) {

    public static final int LENGTH = 6;
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Pattern FORMAT = Pattern.compile("^[A-HJ-NP-Z2-9]{6}$");

    public BookingLocator {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Localizador inválido: " + value);
        }
    }

    /** Acepta minúsculas y espacios alrededor (lo que escribe un usuario en un buscador). */
    public static BookingLocator of(String raw) {
        return new BookingLocator(Objects.requireNonNull(raw, "raw").strip().toUpperCase(Locale.ROOT));
    }

    public static BookingLocator random(RandomGenerator random) {
        var sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new BookingLocator(sb.toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
