package com.insightdevelop.interline.application.booking;

import com.insightdevelop.interline.domain.booking.Booking;
import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.booking.BookingStatus;
import jakarta.enterprise.context.ApplicationScoped;

/** Cancela una reserva no emitida liberando inventario y fondos. Idempotente. */
@ApplicationScoped
public class CancelBookingUseCase {

    private final BookingTransactions tx;
    private final BookingCompensator compensator;

    CancelBookingUseCase(BookingTransactions tx, BookingCompensator compensator) {
        this.tx = tx;
        this.compensator = compensator;
    }

    public Booking cancel(BookingLocator locator, String reason) {
        Booking booking = tx.load(locator);
        if (booking.status() == BookingStatus.CANCELLED) {
            return booking;
        }
        // Falla con 409 antes de tocar nada si el estado no admite cancelación (p. ej. TICKETED).
        booking.status().requireCanTransitionTo(BookingStatus.CANCELLED);
        return compensator.cancel(locator, reason);
    }
}
