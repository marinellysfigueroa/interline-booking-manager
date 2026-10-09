package com.insightdevelop.interline.domain.booking;

import static com.insightdevelop.interline.domain.booking.BookingFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.DomainErrorCode;
import com.insightdevelop.interline.domain.shared.Miles;
import com.insightdevelop.interline.domain.shared.Money;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final Money CASH = Money.of("100.00", "USD");

    @Test
    void cash_payment_cannot_include_miles() {
        assertThatThrownBy(() -> Payment.authorized(PaymentMethod.CASH, CASH, Miles.of(10), "123456", "A", "H", NOW))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("code").isEqualTo(DomainErrorCode.INVALID_PAYMENT);
    }

    @Test
    void miles_payment_cannot_include_cash() {
        assertThatThrownBy(() -> Payment.authorized(PaymentMethod.MILES, CASH, Miles.of(10), "123456", "A", "H", NOW))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void mixed_payment_needs_both_parts() {
        assertThatThrownBy(() -> Payment.authorized(PaymentMethod.MIXED, CASH, Miles.ZERO, null, "A", null, NOW))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void miles_payment_needs_member_and_hold_reference() {
        assertThatThrownBy(() -> Payment.authorized(PaymentMethod.MILES, null, Miles.of(10), null, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void new_payment_is_authorized_and_holds_funds() {
        Payment payment = Payment.authorized(PaymentMethod.MILES, null, Miles.of(10), "123456", null, "HOLD-9", NOW);

        assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.holdsFunds()).isTrue();
        assertThat(payment.cash()).isEmpty();
        assertThat(payment.milesHoldRef()).contains("HOLD-9");
    }
}
