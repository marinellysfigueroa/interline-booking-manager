package com.insightdevelop.interline.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airport.AirportCode;
import com.insightdevelop.interline.domain.booking.Booking;
import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.booking.BookingStatus;
import com.insightdevelop.interline.domain.booking.Contact;
import com.insightdevelop.interline.domain.booking.Fare;
import com.insightdevelop.interline.domain.booking.FlightNumber;
import com.insightdevelop.interline.domain.booking.Passenger;
import com.insightdevelop.interline.domain.booking.Payment;
import com.insightdevelop.interline.domain.booking.PaymentMethod;
import com.insightdevelop.interline.domain.booking.Segment;
import com.insightdevelop.interline.domain.booking.SegmentStatus;
import com.insightdevelop.interline.domain.port.BookingRepository;
import com.insightdevelop.interline.domain.shared.Miles;
import com.insightdevelop.interline.domain.shared.Money;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Ida y vuelta agregado ↔ PostgreSQL y bloqueo optimista, con la BD real de Dev Services. */
@QuarkusTest
class BookingRepositoryAdapterTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Inject
    BookingRepository repository;

    @Test
    void round_trips_the_whole_aggregate() {
        Booking booking = newBooking();
        booking.price(new Fare(Money.of("1120.00", "USD"), Miles.of(112_000)), NOW);
        booking.segments().forEach(s -> booking.updateSegmentStatus(s.id(), SegmentStatus.HK, NOW));
        booking.hold(NOW);
        booking.authorizePayment(Payment.authorized(PaymentMethod.MIXED, Money.of("560.00", "USD"),
                Miles.of(56_000), "12345678", "AUTH-1", "HOLD-1", NOW), NOW);
        QuarkusTransaction.requiringNew().run(() -> repository.save(booking));

        Booking loaded = QuarkusTransaction.requiringNew().call(() ->
                repository.findByLocator(booking.locator()).orElseThrow());

        assertThat(loaded.status()).isEqualTo(BookingStatus.PAYMENT_AUTHORIZED);
        assertThat(loaded.fare()).isEqualTo(booking.fare());
        assertThat(loaded.passengers()).containsExactlyElementsOf(booking.passengers());
        assertThat(loaded.segments()).extracting(Segment::label, Segment::status, Segment::departure)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("AV26 BOG-MAD", SegmentStatus.HK,
                                LocalDateTime.of(2030, 1, 10, 19, 5)),
                        org.assertj.core.groups.Tuple.tuple("IB3234 MAD-FCO", SegmentStatus.HK,
                                LocalDateTime.of(2030, 1, 11, 13, 15)));
        assertThat(loaded.payments()).singleElement().satisfies(p -> {
            assertThat(p.method()).isEqualTo(PaymentMethod.MIXED);
            assertThat(p.cash()).contains(Money.of("560.00", "USD"));
            assertThat(p.milesHoldRef()).contains("HOLD-1");
        });
        assertThat(loaded.statusHistory()).isEqualTo(booking.statusHistory());
        assertThat(QuarkusTransaction.requiringNew().call(() -> repository.findStatus(booking.locator())))
                .contains(BookingStatus.PAYMENT_AUTHORIZED);
    }

    @Test
    void concurrent_updates_are_detected_by_optimistic_locking() throws Exception {
        Booking booking = newBooking();
        QuarkusTransaction.requiringNew().run(() -> repository.save(booking));
        CountDownLatch bothLoaded = new CountDownLatch(2);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<?>> results = List.of(
                    pool.submit(() -> priceConcurrently(booking.locator(), bothLoaded)),
                    pool.submit(() -> priceConcurrently(booking.locator(), bothLoaded)));
            long failures = results.stream().filter(f -> {
                try {
                    f.get();
                    return false;
                } catch (Exception e) {
                    return true;
                }
            }).count();
            assertThat(failures).isEqualTo(1);
        }
    }

    private void priceConcurrently(BookingLocator locator, CountDownLatch bothLoaded) {
        QuarkusTransaction.requiringNew().run(() -> {
            Booking b = repository.findByLocator(locator).orElseThrow();
            bothLoaded.countDown();
            try {
                bothLoaded.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            b.price(new Fare(Money.of("100.00", "USD"), Miles.of(10_000)), Instant.now());
            repository.save(b);
        });
    }

    @Test
    void unknown_locator_is_empty() {
        assertThat(QuarkusTransaction.requiringNew().call(() -> repository.findByLocator(BookingLocator.of("ZZZZZZ"))))
                .isEmpty();
        assertThatThrownBy(() -> BookingLocator.of("bad")).isInstanceOf(IllegalArgumentException.class);
    }

    private static Booking newBooking() {
        Passenger adult = Passenger.adult("Ana", "Pérez", LocalDate.of(1990, 4, 12));
        Passenger infant = Passenger.infant("Sofía", "Pérez", LocalDate.of(2029, 3, 1), adult.id());
        return Booking.create(BookingLocator.random(new SecureRandom()), AirlineCode.of("AV"), "OF-T",
                new Contact("ana@example.com", "+573001234567"), List.of(adult, infant), List.of(
                        Segment.requested(AirlineCode.of("AV"), new FlightNumber("26"), AirportCode.of("BOG"),
                                AirportCode.of("MAD"), LocalDateTime.of(2030, 1, 10, 19, 5),
                                LocalDateTime.of(2030, 1, 11, 11, 40)),
                        Segment.requested(AirlineCode.of("IB"), new FlightNumber("3234"), AirportCode.of("MAD"),
                                AirportCode.of("FCO"), LocalDateTime.of(2030, 1, 11, 13, 15),
                                LocalDateTime.of(2030, 1, 11, 15, 45))), NOW);
    }
}
