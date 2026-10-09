package com.insightdevelop.interline.application.booking;

import com.insightdevelop.interline.domain.booking.Booking;
import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.booking.Payment;
import com.insightdevelop.interline.domain.booking.Segment;
import com.insightdevelop.interline.domain.booking.SegmentStatus;
import com.insightdevelop.interline.domain.port.LoyaltyGateway;
import com.insightdevelop.interline.domain.port.PaymentGateway;
import com.insightdevelop.interline.domain.port.SegmentInventoryGateway;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import org.jboss.logging.Logger;

/**
 * Pasos de compensación compartidos por la saga de emisión, la creación y la cancelación.
 *
 * <p>Cada paso es idempotente en los dos lados: el gateway externo tolera repetir la
 * cancelación/liberación y el agregado ignora un estado ya aplicado. Si el proceso se cae
 * a mitad, volver a ejecutar la compensación completa es seguro.
 */
@ApplicationScoped
public class BookingCompensator {

    private static final Logger LOG = Logger.getLogger(BookingCompensator.class);

    private final BookingTransactions tx;
    private final SegmentInventoryGateway inventory;
    private final PaymentGateway paymentGateway;
    private final LoyaltyGateway loyalty;
    private final Clock clock;

    BookingCompensator(BookingTransactions tx, SegmentInventoryGateway inventory, PaymentGateway paymentGateway,
            LoyaltyGateway loyalty, Clock clock) {
        this.tx = tx;
        this.inventory = inventory;
        this.paymentGateway = paymentGateway;
        this.loyalty = loyalty;
        this.clock = clock;
    }

    /** Cancela segmentos activos y libera pagos; después pasa la reserva a {@code FAILED}. */
    public Booking fail(BookingLocator locator, String reason) {
        releaseResources(locator);
        LOG.infof("Reserva %s compensada y marcada FAILED: %s", locator, reason);
        return tx.update(locator, b -> b.fail(reason, clock.instant()));
    }

    /** Cancela segmentos activos y libera pagos; después pasa la reserva a {@code CANCELLED}. */
    public Booking cancel(BookingLocator locator, String reason) {
        releaseResources(locator);
        return tx.update(locator, b -> b.cancel(reason, clock.instant()));
    }

    private void releaseResources(BookingLocator locator) {
        Booking booking = tx.load(locator);
        for (Segment segment : booking.activeSegments()) {
            inventory.cancel(locator, segment);
            tx.update(locator, b -> b.updateSegmentStatus(segment.id(), SegmentStatus.XX, clock.instant()));
        }
        for (Payment payment : booking.paymentsToRelease()) {
            payment.cashAuthorizationRef().ifPresent(paymentGateway::release);
            payment.milesHoldRef().ifPresent(loyalty::releaseHold);
            tx.update(locator, b -> b.releasePayment(payment.id(), clock.instant()));
        }
    }
}
