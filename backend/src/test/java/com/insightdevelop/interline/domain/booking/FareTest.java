package com.insightdevelop.interline.domain.booking;

import static com.insightdevelop.interline.domain.booking.BookingFixtures.FARE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.Miles;
import com.insightdevelop.interline.domain.shared.Money;
import org.junit.jupiter.api.Test;

class FareTest {

    @Test
    void cash_must_cover_the_total() {
        assertThat(FARE.isCoveredBy(Money.of("1849.60", "USD"), Miles.ZERO)).isTrue();
        assertThat(FARE.isCoveredBy(Money.of("1849.59", "USD"), Miles.ZERO)).isFalse();
    }

    @Test
    void miles_must_cover_the_miles_equivalent() {
        assertThat(FARE.isCoveredBy(null, Miles.of(184_960))).isTrue();
        assertThat(FARE.isCoveredBy(null, Miles.of(184_959))).isFalse();
    }

    @Test
    void mixed_payment_covers_proportionally() {
        assertThat(FARE.isCoveredBy(Money.of("924.80", "USD"), Miles.of(92_480))).isTrue();
        assertThat(FARE.isCoveredBy(Money.of("462.40", "USD"), Miles.of(138_720))).isTrue(); // 25 % + 75 %
        assertThat(FARE.isCoveredBy(Money.of("924.80", "USD"), Miles.of(92_479))).isFalse();
    }

    @Test
    void rejects_a_different_currency() {
        assertThatThrownBy(() -> FARE.isCoveredBy(Money.of("1849.60", "EUR"), Miles.ZERO))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
