package com.insightdevelop.interline.infrastructure.persistence;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airport.AirportCode;
import com.insightdevelop.interline.domain.booking.Booking;
import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.booking.BookingStatus;
import com.insightdevelop.interline.domain.booking.Contact;
import com.insightdevelop.interline.domain.booking.Fare;
import com.insightdevelop.interline.domain.booking.FlightNumber;
import com.insightdevelop.interline.domain.booking.Passenger;
import com.insightdevelop.interline.domain.booking.PassengerId;
import com.insightdevelop.interline.domain.booking.PassengerType;
import com.insightdevelop.interline.domain.booking.Payment;
import com.insightdevelop.interline.domain.booking.PaymentId;
import com.insightdevelop.interline.domain.booking.PaymentMethod;
import com.insightdevelop.interline.domain.booking.PaymentStatus;
import com.insightdevelop.interline.domain.booking.Segment;
import com.insightdevelop.interline.domain.booking.SegmentId;
import com.insightdevelop.interline.domain.booking.SegmentStatus;
import com.insightdevelop.interline.domain.booking.StatusChange;
import com.insightdevelop.interline.domain.booking.Ticket;
import com.insightdevelop.interline.domain.booking.TicketNumber;
import com.insightdevelop.interline.domain.shared.Miles;
import com.insightdevelop.interline.domain.shared.Money;
import com.insightdevelop.interline.infrastructure.persistence.entity.BookingEntity;
import com.insightdevelop.interline.infrastructure.persistence.entity.BookingPassengerEntity;
import com.insightdevelop.interline.infrastructure.persistence.entity.BookingPaymentEntity;
import com.insightdevelop.interline.infrastructure.persistence.entity.BookingSegmentEntity;
import com.insightdevelop.interline.infrastructure.persistence.entity.BookingStatusChangeEntity;
import com.insightdevelop.interline.infrastructure.persistence.entity.BookingTicketEntity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Traducción entre el agregado {@link Booking} y sus entidades JPA. Mapper explícito (sin
 * MapStruct): son pocas clases y así se ve qué cambia en cada actualización.
 */
final class BookingEntityMapper {

    private BookingEntityMapper() {
    }

    // ------------------------------------------------------------------ entidad -> dominio

    static Booking toDomain(BookingEntity e) {
        Fare fare = e.fareAmount == null ? null
                : new Fare(Money.of(e.fareAmount, e.fareCurrency), Miles.of(e.fareMiles));
        return Booking.restore(
                new BookingLocator(e.locator),
                new AirlineCode(e.validatingCarrier),
                e.offerId,
                new Contact(e.contactEmail, e.contactPhone),
                BookingStatus.valueOf(e.status),
                fare,
                e.passengers.stream().map(BookingEntityMapper::toDomain).toList(),
                e.segments.stream().map(BookingEntityMapper::toDomain).toList(),
                e.payments.stream().map(BookingEntityMapper::toDomain).toList(),
                e.tickets.stream().map(t -> new Ticket(new TicketNumber(t.number), new PassengerId(t.passengerId),
                        t.issuedAt)).toList(),
                e.statusHistory.stream().map(h -> new StatusChange(
                        h.fromStatus == null ? null : BookingStatus.valueOf(h.fromStatus),
                        BookingStatus.valueOf(h.toStatus), h.changedAt, h.reason)).toList(),
                e.createdAt,
                e.updatedAt);
    }

    private static Passenger toDomain(BookingPassengerEntity p) {
        return new Passenger(new PassengerId(p.id), PassengerType.valueOf(p.type), p.firstName, p.lastName,
                p.dateOfBirth, p.associatedAdultId == null ? null : new PassengerId(p.associatedAdultId));
    }

    private static Segment toDomain(BookingSegmentEntity s) {
        return Segment.restore(new SegmentId(s.id), new AirlineCode(s.operatingCarrier),
                new FlightNumber(s.flightNumber), new AirportCode(s.origin), new AirportCode(s.destination),
                s.departureLocal, s.arrivalLocal, SegmentStatus.valueOf(s.status));
    }

    private static Payment toDomain(BookingPaymentEntity p) {
        Money cash = p.cashAmount == null ? null : Money.of(p.cashAmount, p.cashCurrency);
        return Payment.restore(new PaymentId(p.id), PaymentMethod.valueOf(p.method), cash, Miles.of(p.miles),
                p.memberNumber, p.cashAuthorizationRef, p.milesHoldRef, p.createdAt, PaymentStatus.valueOf(p.status));
    }

    // ------------------------------------------------------------------ dominio -> entidad

    static BookingEntity newEntity(Booking booking) {
        var e = new BookingEntity();
        e.id = UUID.randomUUID();
        e.locator = booking.locator().value();
        e.validatingCarrier = booking.validatingCarrier().value();
        e.offerId = booking.offerId();
        e.contactEmail = booking.contact().email();
        e.contactPhone = booking.contact().phone();
        List<Segment> segments = booking.segments();
        e.origin = segments.getFirst().origin().value();
        e.destination = segments.getLast().destination().value();
        e.firstDeparture = booking.firstDeparture();
        e.passengerCount = booking.passengers().size();
        e.createdAt = booking.createdAt();
        for (int i = 0; i < booking.passengers().size(); i++) {
            Passenger p = booking.passengers().get(i);
            var pe = new BookingPassengerEntity();
            pe.id = p.id().value();
            pe.booking = e;
            pe.position = i;
            pe.type = p.type().name();
            pe.firstName = p.firstName();
            pe.lastName = p.lastName();
            pe.dateOfBirth = p.dateOfBirth();
            pe.associatedAdultId = p.associatedAdult().map(PassengerId::value).orElse(null);
            e.passengers.add(pe);
        }
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            var se = new BookingSegmentEntity();
            se.id = s.id().value();
            se.booking = e;
            se.position = i;
            se.operatingCarrier = s.operatingCarrier().value();
            se.flightNumber = s.flightNumber().value();
            se.origin = s.origin().value();
            se.destination = s.destination().value();
            se.departureLocal = s.departure();
            se.arrivalLocal = s.arrival();
            e.segments.add(se);
        }
        update(e, booking);
        return e;
    }

    /**
     * Sincroniza el estado mutable del agregado sobre una entidad existente. Pasajeros y
     * datos de vuelo son inmutables; cambian estados, pagos, tickets e historial (solo crece).
     */
    static void update(BookingEntity e, Booking booking) {
        e.status = booking.status().name();
        booking.fare().ifPresent(f -> {
            e.fareAmount = f.total().amount();
            e.fareCurrency = f.total().currency().getCurrencyCode();
            e.fareMiles = f.milesEquivalent().value();
        });
        e.updatedAt = booking.updatedAt();

        Map<UUID, BookingSegmentEntity> segments = e.segments.stream()
                .collect(Collectors.toMap(s -> s.id, Function.identity()));
        booking.segments().forEach(s -> segments.get(s.id().value()).status = s.status().name());

        Map<UUID, BookingPaymentEntity> payments = e.payments.stream()
                .collect(Collectors.toMap(p -> p.id, Function.identity()));
        for (Payment p : booking.payments()) {
            BookingPaymentEntity pe = payments.get(p.id().value());
            if (pe == null) {
                pe = newPayment(e, p);
                e.payments.add(pe);
            }
            pe.status = p.status().name();
        }

        Set<String> issued = e.tickets.stream().map(t -> t.number).collect(Collectors.toSet());
        for (Ticket t : booking.tickets()) {
            if (!issued.contains(t.number().value())) {
                var te = new BookingTicketEntity();
                te.number = t.number().value();
                te.booking = e;
                te.passengerId = t.passengerId().value();
                te.issuedAt = t.issuedAt();
                e.tickets.add(te);
            }
        }

        List<StatusChange> history = booking.statusHistory();
        for (int i = e.statusHistory.size(); i < history.size(); i++) {
            StatusChange c = history.get(i);
            var he = new BookingStatusChangeEntity();
            he.booking = e;
            he.position = i;
            he.fromStatus = c.previous().map(Enum::name).orElse(null);
            he.toStatus = c.to().name();
            he.changedAt = c.at();
            he.reason = c.reason();
            e.statusHistory.add(he);
        }
    }

    private static BookingPaymentEntity newPayment(BookingEntity booking, Payment p) {
        var pe = new BookingPaymentEntity();
        pe.id = p.id().value();
        pe.booking = booking;
        pe.method = p.method().name();
        p.cash().ifPresent(c -> {
            pe.cashAmount = c.amount();
            pe.cashCurrency = c.currency().getCurrencyCode();
        });
        pe.miles = p.miles().value();
        pe.memberNumber = p.memberNumber().orElse(null);
        pe.cashAuthorizationRef = p.cashAuthorizationRef().orElse(null);
        pe.milesHoldRef = p.milesHoldRef().orElse(null);
        pe.createdAt = p.createdAt();
        return pe;
    }
}
