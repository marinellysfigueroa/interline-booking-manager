package com.insightdevelop.interline.application.booking;

import com.insightdevelop.interline.domain.booking.Booking;
import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.port.BookingRepository;
import com.insightdevelop.interline.domain.shared.DomainErrorCode;
import com.insightdevelop.interline.domain.shared.ResourceNotFoundException;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Consumer;

/**
 * Transacciones cortas sobre el agregado. Cada paso de la saga carga, modifica y guarda la
 * reserva en <b>su propia</b> transacción; las llamadas a sistemas externos ocurren entre
 * transacciones, nunca dentro (no se retiene una conexión a la BD durante un reintento).
 *
 * <p>Spring Boot: {@code QuarkusTransaction.requiringNew().call(...)} equivale a un
 * {@code TransactionTemplate} con {@code PROPAGATION_REQUIRES_NEW}.
 */
@ApplicationScoped
public class BookingTransactions {

    private final BookingRepository bookings;

    BookingTransactions(BookingRepository bookings) {
        this.bookings = bookings;
    }

    public Booking load(BookingLocator locator) {
        return QuarkusTransaction.requiringNew().call(() -> find(locator));
    }

    public void create(Booking booking) {
        QuarkusTransaction.requiringNew().run(() -> bookings.save(booking));
    }

    /** Carga, aplica el cambio y guarda en una sola transacción. Devuelve el estado resultante. */
    public Booking update(BookingLocator locator, Consumer<Booking> change) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Booking booking = find(locator);
            change.accept(booking);
            bookings.save(booking);
            return booking;
        });
    }

    private Booking find(BookingLocator locator) {
        return bookings.findByLocator(locator).orElseThrow(() -> notFound(locator));
    }

    static ResourceNotFoundException notFound(BookingLocator locator) {
        return new ResourceNotFoundException(DomainErrorCode.BOOKING_NOT_FOUND,
                "No existe una reserva con localizador " + locator);
    }
}
