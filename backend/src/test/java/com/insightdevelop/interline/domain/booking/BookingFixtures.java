package com.insightdevelop.interline.domain.booking;

import com.insightdevelop.interline.domain.airline.Airline;
import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airline.LoyaltyProgram;
import com.insightdevelop.interline.domain.airport.AirportCode;
import com.insightdevelop.interline.domain.shared.Miles;
import com.insightdevelop.interline.domain.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Datos de prueba: BOG-MAD operado por AV y MAD-FCO operado por IB, validado por AV. */
final class BookingFixtures {

    static final Instant NOW = Instant.parse("2026-11-01T15:00:00Z");
    static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 11, 20);
    static final AirlineCode AV = AirlineCode.of("AV");
    static final AirlineCode IB = AirlineCode.of("IB");
    static final AirlineCode LA = AirlineCode.of("LA");
    static final Fare FARE = new Fare(Money.of("1849.60", "USD"), Miles.of(184_960));

    private BookingFixtures() {
    }

    static Airline avianca(AirlineCode... partners) {
        return new Airline(AV, "Avianca", "134", Optional.of(new LoyaltyProgram("lifemiles")), Set.of(partners));
    }

    static Segment bogMad() {
        return Segment.requested(AV, new FlightNumber("26"), AirportCode.of("BOG"), AirportCode.of("MAD"),
                LocalDateTime.of(2026, 11, 20, 19, 5), LocalDateTime.of(2026, 11, 21, 11, 40));
    }

    static Segment madFco() {
        return Segment.requested(IB, new FlightNumber("3234"), AirportCode.of("MAD"), AirportCode.of("FCO"),
                LocalDateTime.of(2026, 11, 21, 13, 15), LocalDateTime.of(2026, 11, 21, 15, 45));
    }

    static List<Passenger> twoAdultsAndInfant() {
        Passenger ana = Passenger.adult("Ana", "Pérez", LocalDate.of(1990, 4, 12));
        Passenger luis = Passenger.adult("Luis", "Gómez", LocalDate.of(1988, 9, 30));
        Passenger sofia = Passenger.infant("Sofía", "Gómez", LocalDate.of(2026, 1, 15), luis.id());
        return List.of(ana, luis, sofia);
    }

    static Booking draftBooking() {
        return Booking.create(BookingLocator.of("K7Q2MX"), AV, "OF-1", new Contact("ana@example.com", null),
                twoAdultsAndInfant(), List.of(madFco(), bogMad()), NOW);
    }

    static Booking heldBooking() {
        Booking booking = draftBooking();
        booking.price(FARE, NOW);
        booking.hold(NOW);
        return booking;
    }

    static Booking paymentAuthorizedBooking() {
        Booking booking = heldBooking();
        booking.authorizePayment(cashPayment("1849.60"), NOW);
        return booking;
    }

    static void confirmAllSegments(Booking booking) {
        booking.segments().forEach(s -> booking.updateSegmentStatus(s.id(), SegmentStatus.HK, NOW));
    }

    static Payment cashPayment(String amount) {
        return Payment.authorized(PaymentMethod.CASH, Money.of(amount, "USD"), Miles.ZERO, null, "AUTH-1", null, NOW);
    }

    static TicketNumberGenerator sequentialTickets() {
        AtomicLong serial = new AtomicLong(2_412_345_678L);
        return airline -> TicketNumber.of(airline.accountingCode(), serial.getAndIncrement());
    }
}
