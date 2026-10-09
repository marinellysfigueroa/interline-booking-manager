package com.insightdevelop.interline.domain.booking;

import com.insightdevelop.interline.domain.shared.InvalidStateTransitionException;
import java.util.EnumSet;
import java.util.Set;

/**
 * Máquina de estados de la reserva.
 *
 * <pre>
 * DRAFT ─► PRICED ─► HELD ─► PAYMENT_AUTHORIZED ─► TICKETED
 *   │        │        │              │
 *   └────────┴────────┴──────────────┴──► FAILED | CANCELLED
 * </pre>
 *
 * <p>Se modela como {@code enum} con las transiciones válidas declaradas en un
 * {@code switch} exhaustivo (ver ADR 0001, D5): se persiste trivialmente como texto,
 * se serializa sin configuración y el compilador avisa si se añade un estado y no
 * se declaran sus transiciones.
 */
public enum BookingStatus {
    DRAFT,
    PRICED,
    HELD,
    PAYMENT_AUTHORIZED,
    TICKETED,
    FAILED,
    CANCELLED;

    /** Estados a los que se puede pasar desde éste. */
    public Set<BookingStatus> allowedTransitions() {
        return switch (this) {
            case DRAFT -> EnumSet.of(PRICED, FAILED, CANCELLED);
            case PRICED -> EnumSet.of(HELD, FAILED, CANCELLED);
            case HELD -> EnumSet.of(PAYMENT_AUTHORIZED, FAILED, CANCELLED);
            case PAYMENT_AUTHORIZED -> EnumSet.of(TICKETED, FAILED, CANCELLED);
            case TICKETED, FAILED, CANCELLED -> EnumSet.noneOf(BookingStatus.class);
        };
    }

    public boolean canTransitionTo(BookingStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }

    /** @throws InvalidStateTransitionException si la transición no está permitida */
    public void requireCanTransitionTo(BookingStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Reserva", this, target);
        }
    }
}
