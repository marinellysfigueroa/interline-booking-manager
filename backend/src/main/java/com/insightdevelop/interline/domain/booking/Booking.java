package com.insightdevelop.interline.domain.booking;

import com.insightdevelop.interline.domain.airline.Airline;
import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airline.InterlineTicketingPolicy;
import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.DomainErrorCode;
import com.insightdevelop.interline.domain.shared.InvalidStateTransitionException;
import com.insightdevelop.interline.domain.shared.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Raíz del agregado Reserva.
 *
 * <p>Todas las mutaciones pasan por aquí y quedan registradas en {@link #statusHistory()}.
 * Las operaciones que usa la saga son <b>idempotentes</b>: repetir un paso ya aplicado
 * no cambia nada ni falla, de modo que la saga puede reintentarse con seguridad tras una
 * caída a mitad de camino.
 *
 * <p>Las operaciones reciben el instante actual ({@code Instant now}) en lugar de leer
 * el reloj: la capa de aplicación inyecta un {@code java.time.Clock} y las pruebas son
 * deterministas.
 *
 * <p>Spring Boot: no es una {@code @Entity}. La persistencia (fase 2) mapea este objeto
 * a entidades Panache en infraestructura, igual que separarías dominio y JPA en Spring.
 */
public final class Booking {

    private final BookingLocator locator;
    private final AirlineCode validatingCarrier;
    private final String offerId;
    private final Contact contact;
    private final PassengerManifest passengers;
    private final List<Segment> segments;
    private final List<Payment> payments;
    private final List<Ticket> tickets;
    private final List<StatusChange> statusHistory;
    private final Instant createdAt;
    private BookingStatus status;
    private Fare fare;
    private Instant updatedAt;

    private Booking(BookingLocator locator, AirlineCode validatingCarrier, String offerId, Contact contact,
            PassengerManifest passengers, List<Segment> segments, Instant createdAt) {
        this.locator = Objects.requireNonNull(locator, "locator");
        this.validatingCarrier = Objects.requireNonNull(validatingCarrier, "validatingCarrier");
        this.offerId = Objects.requireNonNull(offerId, "offerId");
        this.contact = Objects.requireNonNull(contact, "contact");
        this.passengers = Objects.requireNonNull(passengers, "passengers");
        this.segments = new ArrayList<>(segments);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
        this.payments = new ArrayList<>();
        this.tickets = new ArrayList<>();
        this.statusHistory = new ArrayList<>();
    }

    /**
     * Crea una reserva en {@code DRAFT}.
     *
     * @param segments segmentos en {@code UC}, en cualquier orden (se ordenan por salida)
     * @throws BusinessRuleViolationException si los pasajeros no cumplen las reglas o el itinerario es inválido
     */
    public static Booking create(BookingLocator locator, AirlineCode validatingCarrier, String offerId,
            Contact contact, List<Passenger> passengers, List<Segment> segments, Instant now) {
        Objects.requireNonNull(segments, "segments");
        if (segments.isEmpty()) {
            throw new BusinessRuleViolationException(DomainErrorCode.INVALID_ITINERARY,
                    "La reserva necesita al menos un segmento");
        }
        List<Segment> ordered = segments.stream()
                .sorted(Comparator.comparing(Segment::departure))
                .toList();
        if (ordered.stream().anyMatch(s -> s.status() != SegmentStatus.UC)) {
            throw new IllegalArgumentException("Los segmentos de una reserva nueva deben estar en UC");
        }
        var manifest = PassengerManifest.of(passengers, ordered.getFirst().departure().toLocalDate());
        var booking = new Booking(locator, validatingCarrier, offerId, contact, manifest, ordered, now);
        booking.status = BookingStatus.DRAFT;
        booking.statusHistory.add(new StatusChange(null, BookingStatus.DRAFT, now, "Reserva creada"));
        return booking;
    }

    /**
     * Reconstrucción desde persistencia. No aplica reglas de creación (las reglas se
     * validaron cuando se creó la reserva y las edades no se re-evalúan con el tiempo).
     */
    public static Booking restore(BookingLocator locator, AirlineCode validatingCarrier, String offerId,
            Contact contact, BookingStatus status, Fare fare, List<Passenger> passengers, List<Segment> segments,
            List<Payment> payments, List<Ticket> tickets, List<StatusChange> statusHistory, Instant createdAt,
            Instant updatedAt) {
        var booking = new Booking(locator, validatingCarrier, offerId, contact,
                PassengerManifest.restore(passengers), segments, createdAt);
        booking.status = Objects.requireNonNull(status, "status");
        booking.fare = fare;
        booking.payments.addAll(payments);
        booking.tickets.addAll(tickets);
        booking.statusHistory.addAll(statusHistory);
        booking.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        return booking;
    }

    // ------------------------------------------------------------------ flujo feliz

    /** {@code DRAFT → PRICED}: fija la tarifa confirmada con la oferta. */
    public void price(Fare confirmedFare, Instant now) {
        Objects.requireNonNull(confirmedFare, "confirmedFare");
        transitionTo(BookingStatus.PRICED, "Tarifa confirmada: " + confirmedFare.total(), now);
        this.fare = confirmedFare;
    }

    /** {@code PRICED → HELD}: los segmentos quedan solicitados (HK o UC); ninguno puede estar rechazado. */
    public void hold(Instant now) {
        status.requireCanTransitionTo(BookingStatus.HELD);
        segments.stream().filter(s -> !s.isActive()).findFirst().ifPresent(s -> {
            throw new BusinessRuleViolationException(DomainErrorCode.SEGMENT_REJECTED,
                    "La operadora rechazó el segmento " + s.label());
        });
        transitionTo(BookingStatus.HELD, pendingConfirmation().isEmpty()
                ? "Segmentos confirmados"
                : "Segmentos solicitados; %d pendientes de confirmación".formatted(pendingConfirmation().size()), now);
    }

    /**
     * {@code HELD → PAYMENT_AUTHORIZED}: registra un pago ya autorizado que cubra la tarifa.
     *
     * @throws BusinessRuleViolationException {@code INSUFFICIENT_PAYMENT} o {@code CURRENCY_MISMATCH}
     */
    public void authorizePayment(Payment payment, Instant now) {
        Objects.requireNonNull(payment, "payment");
        status.requireCanTransitionTo(BookingStatus.PAYMENT_AUTHORIZED);
        if (payment.status() != PaymentStatus.AUTHORIZED) {
            throw new IllegalArgumentException("Solo se registran pagos en estado AUTHORIZED");
        }
        if (!fare.isCoveredBy(payment.cash().orElse(null), payment.miles())) {
            throw new BusinessRuleViolationException(DomainErrorCode.INSUFFICIENT_PAYMENT,
                    "El pago no cubre la tarifa de " + fare.total() + " / " + fare.milesEquivalent());
        }
        payments.add(payment);
        transitionTo(BookingStatus.PAYMENT_AUTHORIZED, "Pago %s autorizado".formatted(payment.method()), now);
    }

    /** Captura el pago autorizado antes de emitir. Idempotente. */
    public void capturePayment(Instant now) {
        if (status != BookingStatus.PAYMENT_AUTHORIZED) {
            throw new InvalidStateTransitionException("Pago de la reserva " + locator, status,
                    BookingStatus.PAYMENT_AUTHORIZED);
        }
        Payment payment = activePayment().orElseThrow(() -> new ResourceNotFoundException(
                DomainErrorCode.PAYMENT_NOT_FOUND, "La reserva " + locator + " no tiene un pago activo"));
        if (payment.capture()) {
            touch(now);
        }
    }

    /**
     * {@code PAYMENT_AUTHORIZED → TICKETED}: emite un ticket por pasajero. Idempotente:
     * sobre una reserva ya emitida no hace nada.
     *
     * @throws BusinessRuleViolationException si falta acuerdo interline, hay segmentos sin
     *                                        confirmar o el pago no está capturado
     */
    public void issueTickets(Airline validatingAirline, InterlineTicketingPolicy policy,
            TicketNumberGenerator ticketNumbers, Instant now) {
        if (status == BookingStatus.TICKETED) {
            return;
        }
        status.requireCanTransitionTo(BookingStatus.TICKETED);
        if (!validatingAirline.code().equals(validatingCarrier)) {
            throw new BusinessRuleViolationException(DomainErrorCode.VALIDATING_AIRLINE_MISMATCH,
                    "La reserva la valida %s, no %s".formatted(validatingCarrier, validatingAirline.code()));
        }
        policy.evaluate(validatingAirline, operatingCarriers()).requireEligible();
        List<Segment> notConfirmed = segments.stream().filter(s -> !s.isConfirmed()).toList();
        if (!notConfirmed.isEmpty()) {
            throw new BusinessRuleViolationException(DomainErrorCode.SEGMENTS_NOT_CONFIRMED,
                    "Segmentos sin confirmar: " + labels(notConfirmed));
        }
        boolean captured = activePayment().map(p -> p.status() == PaymentStatus.CAPTURED).orElse(false);
        if (!captured) {
            throw new BusinessRuleViolationException(DomainErrorCode.PAYMENT_NOT_CAPTURED,
                    "El pago debe estar capturado antes de emitir");
        }
        for (Passenger passenger : passengers.all()) {
            tickets.add(new Ticket(ticketNumbers.next(validatingAirline), passenger.id(), now));
        }
        transitionTo(BookingStatus.TICKETED, "%d tickets emitidos".formatted(tickets.size()), now);
    }

    // ------------------------------------------------------------------ segmentos

    /** Aplica el estado informado por la operadora para un segmento. Idempotente. */
    public void updateSegmentStatus(SegmentId segmentId, SegmentStatus newStatus, Instant now) {
        if (status.isTerminal()) {
            throw new InvalidStateTransitionException("Segmentos de la reserva " + locator, status, status);
        }
        if (segment(segmentId).applyStatus(newStatus)) {
            touch(now);
        }
    }

    // ------------------------------------------------------------------ compensación y salidas

    /** Libera un pago autorizado (compensación o cancelación). Idempotente. */
    public void releasePayment(PaymentId paymentId, Instant now) {
        Payment payment = payments.stream().filter(p -> p.id().equals(paymentId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.PAYMENT_NOT_FOUND,
                        "Pago %s no encontrado en la reserva %s".formatted(paymentId.value(), locator)));
        if (payment.release()) {
            touch(now);
        }
    }

    /**
     * Pasa a {@code FAILED}. Idempotente. Exige que la saga haya compensado antes:
     * ningún segmento activo ni pago reteniendo fondos.
     */
    public void fail(String reason, Instant now) {
        if (status == BookingStatus.FAILED) {
            return;
        }
        status.requireCanTransitionTo(BookingStatus.FAILED);
        requireNoHeldResources();
        transitionTo(BookingStatus.FAILED, reason, now);
    }

    /**
     * Pasa a {@code CANCELLED}. Idempotente. Una reserva emitida no se cancela por aquí
     * (requeriría anulación/reembolso). Exige haber cancelado segmentos y liberado pagos.
     */
    public void cancel(String reason, Instant now) {
        if (status == BookingStatus.CANCELLED) {
            return;
        }
        status.requireCanTransitionTo(BookingStatus.CANCELLED);
        requireNoHeldResources();
        transitionTo(BookingStatus.CANCELLED, reason == null || reason.isBlank() ? "Cancelada" : reason, now);
    }

    private void requireNoHeldResources() {
        List<Segment> active = activeSegments();
        boolean holdsFunds = payments.stream().anyMatch(Payment::holdsFunds);
        if (!active.isEmpty() || holdsFunds) {
            throw new BusinessRuleViolationException(DomainErrorCode.RESOURCES_STILL_HELD,
                    "La reserva %s aún retiene %s%s".formatted(locator,
                            active.isEmpty() ? "" : "segmentos activos (" + labels(active) + ")",
                            holdsFunds ? (active.isEmpty() ? "" : " y ") + "fondos o millas" : ""));
        }
    }

    // ------------------------------------------------------------------ consultas

    /** Segmentos que la saga debe cancelar al compensar (HK y UC). */
    public List<Segment> activeSegments() {
        return segments.stream().filter(Segment::isActive).toList();
    }

    /** Segmentos aún en {@code UC}. */
    public List<Segment> pendingConfirmation() {
        return segments.stream().filter(s -> s.status() == SegmentStatus.UC).toList();
    }

    /** Pagos que la saga debe liberar al compensar. */
    public List<Payment> paymentsToRelease() {
        return payments.stream().filter(p -> p.status() == PaymentStatus.AUTHORIZED).toList();
    }

    public Optional<Payment> activePayment() {
        return payments.stream().filter(Payment::holdsFunds).findFirst();
    }

    /** Aerolíneas operadoras del itinerario, sin repetir y en orden de vuelo. */
    public Set<AirlineCode> operatingCarriers() {
        return segments.stream().map(Segment::operatingCarrier)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public LocalDateTime firstDeparture() {
        return segments.getFirst().departure();
    }

    /** Acciones disponibles para el cliente en el estado actual. */
    public Set<BookingAction> availableActions() {
        return switch (status) {
            case DRAFT, PRICED -> EnumSet.of(BookingAction.CANCEL);
            case HELD -> EnumSet.of(BookingAction.PAY, BookingAction.CANCEL);
            case PAYMENT_AUTHORIZED -> EnumSet.of(BookingAction.TICKET, BookingAction.CANCEL);
            case TICKETED, FAILED, CANCELLED -> EnumSet.noneOf(BookingAction.class);
        };
    }

    public Segment segment(SegmentId segmentId) {
        return segments.stream().filter(s -> s.id().equals(segmentId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.SEGMENT_NOT_FOUND,
                        "Segmento %s no encontrado en la reserva %s".formatted(segmentId.value(), locator)));
    }

    // ------------------------------------------------------------------ internos

    private void transitionTo(BookingStatus target, String reason, Instant now) {
        status.requireCanTransitionTo(target);
        statusHistory.add(new StatusChange(status, target, now, reason));
        status = target;
        touch(now);
    }

    private void touch(Instant now) {
        updatedAt = Objects.requireNonNull(now, "now");
    }

    private static String labels(List<Segment> segments) {
        return segments.stream().map(Segment::label).collect(Collectors.joining(", "));
    }

    // ------------------------------------------------------------------ accessors

    public BookingLocator locator() {
        return locator;
    }

    public AirlineCode validatingCarrier() {
        return validatingCarrier;
    }

    public String offerId() {
        return offerId;
    }

    public Contact contact() {
        return contact;
    }

    public BookingStatus status() {
        return status;
    }

    public Optional<Fare> fare() {
        return Optional.ofNullable(fare);
    }

    public List<Passenger> passengers() {
        return passengers.all();
    }

    public PassengerCounts passengerCounts() {
        return passengers.counts();
    }

    public List<Segment> segments() {
        return Collections.unmodifiableList(segments);
    }

    public List<Payment> payments() {
        return Collections.unmodifiableList(payments);
    }

    public List<Ticket> tickets() {
        return Collections.unmodifiableList(tickets);
    }

    public List<StatusChange> statusHistory() {
        return Collections.unmodifiableList(statusHistory);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Booking other && locator.equals(other.locator);
    }

    @Override
    public int hashCode() {
        return locator.hashCode();
    }

    @Override
    public String toString() {
        return "Booking[" + locator + ", " + status + "]";
    }
}
