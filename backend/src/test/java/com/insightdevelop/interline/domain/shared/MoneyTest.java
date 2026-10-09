package com.insightdevelop.interline.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizes_scale_to_the_currency_so_equal_amounts_are_equal() {
        assertThat(Money.of("10", "USD")).isEqualTo(Money.of("10.00", "USD"));
        assertThat(Money.of("1500", "JPY").amount().scale()).isZero();
    }

    @Test
    void rejects_more_decimals_than_the_currency_allows_instead_of_rounding() {
        assertThatThrownBy(() -> Money.of("10.005", "USD")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_negative_amounts() {
        assertThatThrownBy(() -> Money.of("-1", "USD")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adds_only_the_same_currency() {
        assertThat(Money.of("1.10", "USD").plus(Money.of("2.20", "USD"))).isEqualTo(Money.of("3.30", "USD"));
        assertThatThrownBy(() -> Money.of("1", "USD").plus(Money.of("1", "EUR")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("code").isEqualTo(DomainErrorCode.CURRENCY_MISMATCH);
    }
}
