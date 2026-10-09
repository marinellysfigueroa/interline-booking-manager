package com.insightdevelop.interline.application.booking;

import com.insightdevelop.interline.domain.booking.Booking;
import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.booking.BookingStatus;
import com.insightdevelop.interline.domain.port.BookingRepository;
import com.insightdevelop.interline.domain.shared.Page;
import com.insightdevelop.interline.domain.shared.PageRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Optional;

/** Lado de lectura: consulta y listado de reservas. */
@ApplicationScoped
public class BookingQueries {

    private final BookingRepository bookings;

    BookingQueries(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @Transactional
    public Booking get(BookingLocator locator) {
        return bookings.findByLocator(locator).orElseThrow(() -> BookingTransactions.notFound(locator));
    }

    @Transactional
    public Page<Booking> list(BookingRepository.Query query, PageRequest pageRequest) {
        return bookings.search(query, pageRequest);
    }

    @Transactional
    public Optional<BookingStatus> statusOf(BookingLocator locator) {
        return bookings.findStatus(locator);
    }
}
