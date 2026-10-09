package com.insightdevelop.interline.application.booking;

import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.booking.BookingStatus;
import com.insightdevelop.interline.domain.shared.DomainException;

/**
 * Un flujo de varios pasos falló y la reserva quedó compensada (normalmente en
 * {@code FAILED}). Envuelve la causa de dominio y añade el localizador y el estado final,
 * que viajan en el Problem Details para que el cliente sepa qué pasó con su reserva.
 */
public class BookingSagaFailedException extends RuntimeException {

    private final BookingLocator locator;
    private final BookingStatus bookingStatus;

    public BookingSagaFailedException(BookingLocator locator, BookingStatus bookingStatus, DomainException reason) {
        super(reason.getMessage(), reason);
        this.locator = locator;
        this.bookingStatus = bookingStatus;
    }

    public BookingLocator locator() {
        return locator;
    }

    public BookingStatus bookingStatus() {
        return bookingStatus;
    }

    public DomainException reason() {
        return (DomainException) getCause();
    }
}
