package com.insightdevelop.interline.application.booking;

import com.insightdevelop.interline.domain.booking.Booking;
import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.booking.BookingStatus;
import com.insightdevelop.interline.domain.booking.Fare;
import com.insightdevelop.interline.domain.booking.Payment;
import com.insightdevelop.interline.domain.port.LoyaltyGateway;
import com.insightdevelop.interline.domain.port.PaymentGateway;
import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.DomainErrorCode;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import org.jboss.logging.Logger;

/**
 * Autoriza el pago (efectivo, millas o mixto): {@code HELD → PAYMENT_AUTHORIZED}.
 *
 * <p>Se valida todo lo posible <b>antes</b> de tocar la pasarela (estado y cobertura) para
 * no crear autorizaciones que haya que anular. Si algo falla después de autorizar, se
 * liberan las autorizaciones huérfanas.
 */
@ApplicationScoped
public class AuthorizePaymentUseCase {

    private static final Logger LOG = Logger.getLogger(AuthorizePaymentUseCase.class);

    private final BookingTransactions tx;
    private final PaymentGateway paymentGateway;
    private final LoyaltyGateway loyalty;
    private final Clock clock;

    AuthorizePaymentUseCase(BookingTransactions tx, PaymentGateway paymentGateway, LoyaltyGateway loyalty,
            Clock clock) {
        this.tx = tx;
        this.paymentGateway = paymentGateway;
        this.loyalty = loyalty;
        this.clock = clock;
    }

    /** @param idempotencyKey se propaga a la pasarela y al programa de lealtad para no duplicar cargos */
    public Booking authorize(BookingLocator locator, PaymentCommand command, String idempotencyKey) {
        Booking booking = tx.load(locator);
        booking.status().requireCanTransitionTo(BookingStatus.PAYMENT_AUTHORIZED);
        Fare fare = booking.fare().orElseThrow();
        if (!fare.isCoveredBy(command.cash(), command.miles())) {
            throw new BusinessRuleViolationException(DomainErrorCode.INSUFFICIENT_PAYMENT,
                    "El pago no cubre la tarifa de %s / %s".formatted(fare.total(), fare.milesEquivalent()));
        }

        String cashRef = null;
        String holdRef = null;
        try {
            if (command.method().usesCash()) {
                cashRef = paymentGateway.authorize(locator, command.cash(), idempotencyKey + ":cash");
            }
            if (command.method().usesMiles()) {
                holdRef = loyalty.holdMiles(booking.validatingCarrier(), command.memberNumber(), command.miles(),
                        idempotencyKey + ":miles");
            }
            Payment payment = Payment.authorized(command.method(), command.cash(), command.miles(),
                    command.memberNumber(), cashRef, holdRef, clock.instant());
            return tx.update(locator, b -> b.authorizePayment(payment, clock.instant()));
        } catch (RuntimeException e) {
            releaseOrphans(cashRef, holdRef, e);
            throw e;
        }
    }

    private void releaseOrphans(String cashRef, String holdRef, RuntimeException cause) {
        try {
            if (cashRef != null) {
                paymentGateway.release(cashRef);
            }
            if (holdRef != null) {
                loyalty.releaseHold(holdRef);
            }
        } catch (RuntimeException releaseError) {
            LOG.errorf(releaseError, "No se pudieron liberar las autorizaciones %s / %s", cashRef, holdRef);
            cause.addSuppressed(releaseError);
        }
    }
}
