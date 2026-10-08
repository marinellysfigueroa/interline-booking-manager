package com.insightdevelop.interline.domain.booking;

import java.util.Objects;
import java.util.Optional;

/** Datos de contacto del titular de la reserva. El formato se valida en el borde (contrato). */
public record Contact(String email, String phone) {

    public Contact {
        Objects.requireNonNull(email, "email");
        if (email.isBlank()) {
            throw new IllegalArgumentException("El email de contacto es obligatorio");
        }
    }

    public Optional<String> phoneNumber() {
        return Optional.ofNullable(phone);
    }
}
