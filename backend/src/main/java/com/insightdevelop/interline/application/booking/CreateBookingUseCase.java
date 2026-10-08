package com.insightdevelop.interline.application.booking;

import com.insightdevelop.interline.domain.booking.Booking;
import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.booking.BookingStatus;
import com.insightdevelop.interline.domain.booking.Passenger;
import com.insightdevelop.interline.domain.booking.PassengerId;
import com.insightdevelop.interline.domain.booking.PassengerType;
import com.insightdevelop.interline.domain.booking.Segment;
import com.insightdevelop.interline.domain.booking.SegmentStatus;
import com.insightdevelop.interline.domain.offer.FlightOffer;
import com.insightdevelop.interline.domain.offer.OfferSegment;
import com.insightdevelop.interline.domain.port.BookingRepository;
import com.insightdevelop.interline.domain.port.OfferStore;
import com.insightdevelop.interline.domain.port.SegmentInventoryGateway;
import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.DomainErrorCode;
import com.insightdevelop.interline.domain.shared.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Crea una reserva a partir de una oferta: {@code DRAFT → PRICED → HELD}.
 *
 * <ol>
 *   <li>Valida la oferta (existe, no caducó, coincide con los pasajeros).</li>
 *   <li>Crea y tarifica la reserva (transacción 1).</li>
 *   <li>Solicita cada segmento a su operadora, fuera de transacción, y registra la respuesta.</li>
 *   <li>Si una operadora rechaza un segmento (XX), compensa y la reserva queda {@code FAILED}.</li>
 *   <li>Si no, pasa a {@code HELD} (algunos segmentos pueden seguir UC hasta la emisión).</li>
 * </ol>
 */
@ApplicationScoped
public class CreateBookingUseCase {

    private static final int MAX_LOCATOR_ATTEMPTS = 5;

    private final OfferStore offers;
    private final BookingRepository bookings;
    private final BookingTransactions tx;
    private final SegmentInventoryGateway inventory;
    private final BookingCompensator compensator;
    private final Clock clock;
    private final RandomGenerator random = new SecureRandom();

    CreateBookingUseCase(OfferStore offers, BookingRepository bookings, BookingTransactions tx,
            SegmentInventoryGateway inventory, BookingCompensator compensator, Clock clock) {
        this.offers = offers;
        this.bookings = bookings;
        this.tx = tx;
        this.inventory = inventory;
        this.compensator = compensator;
        this.clock = clock;
    }

    public Booking create(CreateBookingCommand command) {
        Instant now = clock.instant();
        FlightOffer offer = offers.findById(command.offerId()).orElseThrow(() -> new ResourceNotFoundException(
                DomainErrorCode.OFFER_NOT_FOUND, "La oferta %s no existe".formatted(command.offerId())));
        if (offer.isExpiredAt(now)) {
            throw new BusinessRuleViolationException(DomainErrorCode.OFFER_EXPIRED,
                    "La oferta %s caducó a las %s; vuelve a buscar".formatted(offer.offerId(), offer.expiresAt()));
        }
        if (command.tenant() != null && !command.tenant().equals(offer.validatingCarrier())) {
            throw new BusinessRuleViolationException(DomainErrorCode.VALIDATING_AIRLINE_MISMATCH,
                    "La oferta la valida %s y se está reservando desde %s"
                            .formatted(offer.validatingCarrier(), command.tenant()));
        }

        List<Segment> segments = offer.allSegments().stream().map(OfferSegment::toRequestedSegment).toList();
        Booking booking = Booking.create(newLocator(), offer.validatingCarrier(), offer.offerId(), command.contact(),
                resolvePassengers(command.passengers()), segments, now);
        if (!booking.passengerCounts().equals(offer.passengers())) {
            throw new BusinessRuleViolationException(DomainErrorCode.PASSENGER_MISMATCH,
                    "La oferta se tarificó para %s y la reserva trae %s"
                            .formatted(offer.passengers(), booking.passengerCounts()));
        }
        booking.price(offer.fare(), now);
        tx.create(booking);

        BookingLocator locator = booking.locator();
        int seats = booking.passengerCounts().seated();
        for (Segment segment : booking.segments()) {
            SegmentStatus status;
            try {
                status = inventory.sell(locator, segment, seats);
            } catch (RuntimeException e) {
                compensator.fail(locator, "Error al solicitar " + segment.label() + ": " + e.getMessage());
                throw e;
            }
            tx.update(locator, b -> b.updateSegmentStatus(segment.id(), status, clock.instant()));
            if (status == SegmentStatus.XX) {
                String reason = "La operadora rechazó el segmento " + segment.label();
                compensator.fail(locator, reason);
                throw new BookingSagaFailedException(locator, BookingStatus.FAILED,
                        new BusinessRuleViolationException(DomainErrorCode.SEGMENT_REJECTED, reason));
            }
        }
        return tx.update(locator, b -> b.hold(clock.instant()));
    }

    private BookingLocator newLocator() {
        for (int i = 0; i < MAX_LOCATOR_ATTEMPTS; i++) {
            BookingLocator candidate = BookingLocator.random(random);
            if (!bookings.existsByLocator(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No se pudo generar un localizador libre");
    }

    /** Traduce las referencias locales del cliente ({@code ref}) a identificadores de pasajero. */
    static List<Passenger> resolvePassengers(List<CreateBookingCommand.PassengerDraft> drafts) {
        Map<String, PassengerId> ids = new HashMap<>();
        for (var draft : drafts) {
            if (ids.putIfAbsent(draft.ref(), PassengerId.random()) != null) {
                throw new IllegalArgumentException("Referencia de pasajero duplicada: " + draft.ref());
            }
        }
        return drafts.stream().map(draft -> {
            PassengerId adultId = null;
            if (draft.type() == PassengerType.INF) {
                adultId = ids.get(draft.associatedAdultRef());
                if (adultId == null) {
                    throw new BusinessRuleViolationException(DomainErrorCode.INFANT_RULE_VIOLATION,
                            "El infante %s debe indicar en associatedAdultRef la ref de un adulto de la reserva"
                                    .formatted(draft.ref()));
                }
            } else if (draft.associatedAdultRef() != null) {
                throw new IllegalArgumentException("Solo un infante (INF) puede indicar associatedAdultRef");
            }
            return new Passenger(ids.get(draft.ref()), draft.type(), draft.firstName(), draft.lastName(),
                    draft.dateOfBirth(), adultId);
        }).toList();
    }
}
