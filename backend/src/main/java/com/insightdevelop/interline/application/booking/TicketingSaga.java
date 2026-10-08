package com.insightdevelop.interline.application.booking;

import com.insightdevelop.interline.application.config.SagaConfig;
import com.insightdevelop.interline.domain.airline.Airline;
import com.insightdevelop.interline.domain.airline.InterlineTicketingPolicy;
import com.insightdevelop.interline.domain.booking.Booking;
import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.booking.BookingStatus;
import com.insightdevelop.interline.domain.booking.Payment;
import com.insightdevelop.interline.domain.booking.PaymentStatus;
import com.insightdevelop.interline.domain.booking.Segment;
import com.insightdevelop.interline.domain.booking.SegmentStatus;
import com.insightdevelop.interline.domain.booking.TicketNumberGenerator;
import com.insightdevelop.interline.domain.port.AirlineRepository;
import com.insightdevelop.interline.domain.port.LoyaltyGateway;
import com.insightdevelop.interline.domain.port.PaymentGateway;
import com.insightdevelop.interline.domain.port.SegmentInventoryGateway;
import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.DomainErrorCode;
import com.insightdevelop.interline.domain.shared.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import org.jboss.logging.Logger;

/**
 * Saga <b>orquestada</b> de emisión del ticket único.
 *
 * <pre>
 *  1. Regla interline        ── falla ─► 422, la reserva no cambia
 *  2. Confirmar segmentos UC ── sigue UC/XX tras N intentos ─► compensar ─► FAILED (422)
 *     (con reintentos y backoff)
 *  3. Capturar pago / redimir millas
 *  4. Emitir tickets ─► TICKETED
 *
 *  Compensación: cancelar segmentos activos → liberar autorización / hold de millas → FAILED
 * </pre>
 *
 * <p>El estado de la saga es el propio agregado (estados de reserva, segmentos y pagos):
 * no hace falta una tabla aparte. Como cada paso es idempotente, si el proceso muere a
 * mitad basta con volver a llamar a {@code POST /ticket}: los pasos ya aplicados no hacen
 * nada y la saga continúa donde se quedó.
 *
 * <p>Los reintentos de confirmación son un bucle explícito (y no {@code @Retry} de
 * MicroProfile Fault Tolerance) porque "seguir en UC" no es un error técnico sino una
 * respuesta de negocio. {@code @Retry} se reserva para fallos de red con el proveedor
 * (fase 3).
 */
@ApplicationScoped
public class TicketingSaga {

    private static final Logger LOG = Logger.getLogger(TicketingSaga.class);

    private final BookingTransactions tx;
    private final BookingCompensator compensator;
    private final AirlineRepository airlines;
    private final InterlineTicketingPolicy policy;
    private final TicketNumberGenerator ticketNumbers;
    private final SegmentInventoryGateway inventory;
    private final PaymentGateway paymentGateway;
    private final LoyaltyGateway loyalty;
    private final SagaConfig config;
    private final Clock clock;

    TicketingSaga(BookingTransactions tx, BookingCompensator compensator, AirlineRepository airlines,
            InterlineTicketingPolicy policy, TicketNumberGenerator ticketNumbers, SegmentInventoryGateway inventory,
            PaymentGateway paymentGateway, LoyaltyGateway loyalty, SagaConfig config, Clock clock) {
        this.tx = tx;
        this.compensator = compensator;
        this.airlines = airlines;
        this.policy = policy;
        this.ticketNumbers = ticketNumbers;
        this.inventory = inventory;
        this.paymentGateway = paymentGateway;
        this.loyalty = loyalty;
        this.config = config;
        this.clock = clock;
    }

    public Booking issue(BookingLocator locator) {
        Booking booking = tx.load(locator);
        if (booking.status() == BookingStatus.TICKETED) {
            return booking; // idempotente: ya emitida
        }
        booking.status().requireCanTransitionTo(BookingStatus.TICKETED);

        // 1. Regla interline (sin efectos secundarios si falla)
        Airline validating = airlines.findByCode(booking.validatingCarrier())
                .orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.AIRLINE_NOT_FOUND,
                        "Aerolínea validadora desconocida: " + booking.validatingCarrier()));
        policy.evaluate(validating, booking.operatingCarriers()).requireEligible();

        // 2. Confirmar segmentos UC con reintentos
        for (Segment segment : booking.pendingConfirmation()) {
            SegmentStatus status = confirmWithRetries(locator, segment);
            if (status != SegmentStatus.HK) {
                String reason = "El segmento %s siguió en %s tras %d intentos; la reserva se compensó"
                        .formatted(segment.label(), status, config.segmentConfirmation().maxAttempts());
                compensator.fail(locator, reason);
                throw new BookingSagaFailedException(locator, BookingStatus.FAILED,
                        new BusinessRuleViolationException(DomainErrorCode.SEGMENT_NOT_CONFIRMED, reason));
            }
        }

        // 3. Capturar el pago (último paso antes de emitir: después ya no hay compensación posible)
        Payment payment = tx.load(locator).activePayment().orElseThrow(() -> new BusinessRuleViolationException(
                DomainErrorCode.PAYMENT_NOT_CAPTURED, "La reserva " + locator + " no tiene un pago activo"));
        if (payment.status() == PaymentStatus.AUTHORIZED) {
            payment.cashAuthorizationRef().ifPresent(paymentGateway::capture);
            payment.milesHoldRef().ifPresent(loyalty::redeem);
            tx.update(locator, b -> b.capturePayment(clock.instant()));
        }

        // 4. Emitir
        Booking ticketed = tx.update(locator,
                b -> b.issueTickets(validating, policy, ticketNumbers, clock.instant()));
        LOG.infof("Reserva %s emitida con %d tickets", locator, ticketed.tickets().size());
        return ticketed;
    }

    private SegmentStatus confirmWithRetries(BookingLocator locator, Segment segment) {
        SagaConfig.SegmentConfirmation retry = config.segmentConfirmation();
        Duration delay = retry.initialDelay();
        SegmentStatus status = SegmentStatus.UC;
        for (int attempt = 1; attempt <= retry.maxAttempts(); attempt++) {
            status = inventory.checkStatus(locator, segment);
            SegmentStatus reported = status;
            tx.update(locator, b -> b.updateSegmentStatus(segment.id(), reported, clock.instant()));
            if (status != SegmentStatus.UC) {
                return status;
            }
            LOG.debugf("%s sigue UC (intento %d/%d)", segment.label(), attempt, retry.maxAttempts());
            if (attempt < retry.maxAttempts()) {
                sleep(delay);
                delay = Duration.ofMillis((long) (delay.toMillis() * retry.backoffMultiplier()));
            }
        }
        return status;
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Saga interrumpida", e);
        }
    }
}
