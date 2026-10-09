package com.insightdevelop.interline.domain.booking;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Entrada de la línea de tiempo de estados de la reserva.
 *
 * @param from   estado anterior; {@code null} en la creación
 * @param reason motivo legible (opcional)
 */
public record StatusChange(BookingStatus from, BookingStatus to, Instant at, String reason) {

    public StatusChange {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(at, "at");
    }

    public Optional<BookingStatus> previous() {
        return Optional.ofNullable(from);
    }
}
