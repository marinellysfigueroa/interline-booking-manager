package com.insightdevelop.interline.domain.booking;

import static com.insightdevelop.interline.domain.booking.BookingStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.insightdevelop.interline.domain.shared.InvalidStateTransitionException;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class BookingStatusTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "DRAFT, PRICED", "PRICED, HELD", "HELD, PAYMENT_AUTHORIZED", "PAYMENT_AUTHORIZED, TICKETED",
            "DRAFT, FAILED", "PRICED, CANCELLED", "HELD, FAILED", "PAYMENT_AUTHORIZED, CANCELLED"
    })
    void allows_happy_path_and_exits(BookingStatus from, BookingStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "DRAFT, HELD", "PRICED, TICKETED", "HELD, TICKETED", "TICKETED, CANCELLED",
            "FAILED, DRAFT", "CANCELLED, HELD", "HELD, PRICED", "HELD, HELD"
    })
    void rejects_skips_backward_moves_and_leaving_terminal_states(BookingStatus from, BookingStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
        assertThatThrownBy(() -> from.requireCanTransitionTo(to))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void only_ticketed_failed_and_cancelled_are_terminal() {
        assertThat(EnumSet.allOf(BookingStatus.class).stream().filter(BookingStatus::isTerminal))
                .containsExactlyInAnyOrder(TICKETED, FAILED, CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"TICKETED", "FAILED", "CANCELLED"})
    void every_non_terminal_state_can_exit_to_failed_or_cancelled(BookingStatus status) {
        assertThat(status.allowedTransitions()).contains(FAILED, CANCELLED);
    }
}
