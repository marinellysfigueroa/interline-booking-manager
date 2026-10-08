package com.insightdevelop.interline.domain.booking;

import java.time.Instant;
import java.util.Objects;

/** Ticket electrónico emitido para un pasajero (los infantes también reciben ticket). */
public record Ticket(TicketNumber number, PassengerId passengerId, Instant issuedAt) {

    public Ticket {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(passengerId, "passengerId");
        Objects.requireNonNull(issuedAt, "issuedAt");
    }
}
