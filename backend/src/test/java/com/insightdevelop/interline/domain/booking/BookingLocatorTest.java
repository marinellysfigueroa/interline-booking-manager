package com.insightdevelop.interline.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BookingLocatorTest {

    private final RandomGenerator random = new SplittableRandom(42);

    @RepeatedTest(50)
    void generates_six_unambiguous_characters() {
        assertThat(BookingLocator.random(random).value()).matches("^[A-HJ-NP-Z2-9]{6}$");
    }

    @Test
    void normalizes_user_input() {
        assertThat(BookingLocator.of(" k7q2mx ").value()).isEqualTo("K7Q2MX");
    }

    @ParameterizedTest
    @ValueSource(strings = {"K7Q2M", "K7Q2MXX", "K7Q2M0", "K7Q2MI", "K7Q-MX"})
    void rejects_invalid_locators(String raw) {
        assertThatThrownBy(() -> BookingLocator.of(raw)).isInstanceOf(IllegalArgumentException.class);
    }
}
