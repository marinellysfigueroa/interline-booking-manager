package com.insightdevelop.interline.domain.booking;

import static com.insightdevelop.interline.domain.booking.BookingFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.insightdevelop.interline.domain.airline.InterlineTicketingPolicy;
import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.DomainErrorCode;
import com.insightdevelop.interline.domain.shared.InvalidStateTransitionException;
import com.insightdevelop.interline.domain.shared.Miles;
import com.insightdevelop.interline.domain.shared.Money;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BookingTest {

    private final InterlineTicketingPolicy policy = new InterlineTicketingPolicy();
    private final Instant later = NOW.plusSeconds(60);

    @Nested
    class Creation {

        @Test
        void starts_in_draft_with_segments_ordered_by_departure() {
            Booking booking = draftBooking();

            assertThat(booking.status()).isEqualTo(BookingStatus.DRAFT);
            assertThat(booking.segments()).extracting(Segment::label)
                    .containsExactly("AV26 BOG-MAD", "IB3234 MAD-FCO");
            assertThat(booking.segments()).allMatch(s -> s.status() == SegmentStatus.UC);
            assertThat(booking.operatingCarriers()).containsExactly(AV, IB);
            assertThat(booking.statusHistory()).singleElement()
                    .satisfies(change -> assertThat(change.previous()).isEmpty());
        }
    }

    @Nested
    class HappyPath {

        @Test
        void goes_from_draft_to_ticketed_recording_the_timeline() {
            Booking booking = draftBooking();
            booking.price(FARE, NOW);
            booking.hold(NOW);
            booking.authorizePayment(cashPayment("1849.60"), NOW);
            confirmAllSegments(booking);
            booking.capturePayment(NOW);
            booking.issueTickets(avianca(IB), policy, sequentialTickets(), later);

            assertThat(booking.status()).isEqualTo(BookingStatus.TICKETED);
            assertThat(booking.statusHistory()).extracting(StatusChange::to).containsExactly(
                    BookingStatus.DRAFT, BookingStatus.PRICED, BookingStatus.HELD,
                    BookingStatus.PAYMENT_AUTHORIZED, BookingStatus.TICKETED);
            assertThat(booking.tickets()).hasSize(3)
                    .allSatisfy(t -> assertThat(t.number().accountingCode()).isEqualTo("134"));
            assertThat(booking.payments()).singleElement()
                    .extracting(Payment::status).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(booking.updatedAt()).isEqualTo(later);
            assertThat(booking.availableActions()).isEmpty();
        }

        @Test
        void issuing_twice_is_idempotent() {
            Booking booking = paymentAuthorizedBooking();
            confirmAllSegments(booking);
            booking.capturePayment(NOW);
            TicketNumberGenerator generator = sequentialTickets();

            booking.issueTickets(avianca(IB), policy, generator, NOW);
            booking.issueTickets(avianca(IB), policy, generator, later);

            assertThat(booking.tickets()).hasSize(3);
            assertThat(booking.statusHistory()).hasSize(5);
        }

        @Test
        void exposes_available_actions_per_state() {
            Booking booking = draftBooking();
            assertThat(booking.availableActions()).containsExactly(BookingAction.CANCEL);
            booking.price(FARE, NOW);
            booking.hold(NOW);
            assertThat(booking.availableActions()).containsExactly(BookingAction.PAY, BookingAction.CANCEL);
            booking.authorizePayment(cashPayment("1849.60"), NOW);
            assertThat(booking.availableActions()).containsExactly(BookingAction.TICKET, BookingAction.CANCEL);
        }
    }

    @Nested
    class StateMachineGuards {

        @Test
        void cannot_skip_steps() {
            Booking booking = draftBooking();

            assertThatThrownBy(() -> booking.hold(NOW)).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> booking.authorizePayment(cashPayment("1849.60"), NOW))
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThat(booking.status()).isEqualTo(BookingStatus.DRAFT);
        }

        @Test
        void cannot_hold_when_an_operator_rejected_a_segment() {
            Booking booking = draftBooking();
            booking.price(FARE, NOW);
            booking.updateSegmentStatus(booking.segments().get(1).id(), SegmentStatus.XX, NOW);

            assertThatThrownBy(() -> booking.hold(NOW))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("code").isEqualTo(DomainErrorCode.SEGMENT_REJECTED);
        }

        @Test
        void a_ticketed_booking_cannot_be_cancelled() {
            Booking booking = paymentAuthorizedBooking();
            confirmAllSegments(booking);
            booking.capturePayment(NOW);
            booking.issueTickets(avianca(IB), policy, sequentialTickets(), NOW);

            assertThatThrownBy(() -> booking.cancel("cambio de planes", NOW))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    class Payments {

        @Test
        void rejects_a_payment_that_does_not_cover_the_fare() {
            Booking booking = heldBooking();

            assertThatThrownBy(() -> booking.authorizePayment(cashPayment("1000.00"), NOW))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("code").isEqualTo(DomainErrorCode.INSUFFICIENT_PAYMENT);
            assertThat(booking.status()).isEqualTo(BookingStatus.HELD);
            assertThat(booking.payments()).isEmpty();
        }

        @Test
        void accepts_a_mixed_payment_that_covers_the_fare() {
            Booking booking = heldBooking();
            Payment mixed = Payment.authorized(PaymentMethod.MIXED, Money.of("924.80", "USD"), Miles.of(92_480),
                    "12345678901", "AUTH-1", "HOLD-1", NOW);

            booking.authorizePayment(mixed, NOW);

            assertThat(booking.status()).isEqualTo(BookingStatus.PAYMENT_AUTHORIZED);
        }

        @Test
        void capturing_twice_is_idempotent() {
            Booking booking = paymentAuthorizedBooking();

            booking.capturePayment(NOW);
            booking.capturePayment(later);

            assertThat(booking.activePayment()).get().extracting(Payment::status).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(booking.updatedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    class InterlineRule {

        @Test
        void refuses_single_ticket_without_agreement_with_every_operating_carrier() {
            Booking booking = paymentAuthorizedBooking();
            confirmAllSegments(booking);
            booking.capturePayment(NOW);

            assertThatThrownBy(() -> booking.issueTickets(avianca(LA), policy, sequentialTickets(), NOW))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("IB")
                    .extracting("code").isEqualTo(DomainErrorCode.INTERLINE_AGREEMENT_MISSING);
            assertThat(booking.status()).isEqualTo(BookingStatus.PAYMENT_AUTHORIZED);
            assertThat(booking.tickets()).isEmpty();
        }

        @Test
        void refuses_to_issue_with_unconfirmed_segments() {
            Booking booking = paymentAuthorizedBooking();
            booking.updateSegmentStatus(booking.segments().getFirst().id(), SegmentStatus.HK, NOW);
            booking.capturePayment(NOW);

            assertThatThrownBy(() -> booking.issueTickets(avianca(IB), policy, sequentialTickets(), NOW))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("IB3234 MAD-FCO")
                    .extracting("code").isEqualTo(DomainErrorCode.SEGMENTS_NOT_CONFIRMED);
        }

        @Test
        void refuses_to_issue_before_capturing_the_payment() {
            Booking booking = paymentAuthorizedBooking();
            confirmAllSegments(booking);

            assertThatThrownBy(() -> booking.issueTickets(avianca(IB), policy, sequentialTickets(), NOW))
                    .extracting("code").isEqualTo(DomainErrorCode.PAYMENT_NOT_CAPTURED);
        }
    }

    /** Pasos de dominio que usará la saga de compensación (fase 2). */
    @Nested
    class CompensationSteps {

        @Test
        void fails_only_after_releasing_inventory_and_funds() {
            Booking booking = paymentAuthorizedBooking();
            Segment bogMad = booking.segments().getFirst();
            booking.updateSegmentStatus(bogMad.id(), SegmentStatus.HK, NOW); // MAD-FCO sigue en UC

            assertThatThrownBy(() -> booking.fail("MAD-FCO sigue UC", NOW))
                    .extracting("code").isEqualTo(DomainErrorCode.RESOURCES_STILL_HELD);

            booking.activeSegments().forEach(s -> booking.updateSegmentStatus(s.id(), SegmentStatus.XX, NOW));
            booking.paymentsToRelease().forEach(p -> booking.releasePayment(p.id(), NOW));
            booking.fail("MAD-FCO sigue UC", later);

            assertThat(booking.status()).isEqualTo(BookingStatus.FAILED);
            assertThat(booking.segments()).allMatch(s -> s.status() == SegmentStatus.XX);
            assertThat(booking.payments()).allMatch(p -> p.status() == PaymentStatus.RELEASED);
        }

        @Test
        void every_compensation_step_is_idempotent() {
            Booking booking = paymentAuthorizedBooking();
            Segment segment = booking.segments().getFirst();
            Payment payment = booking.payments().getFirst();

            booking.updateSegmentStatus(segment.id(), SegmentStatus.XX, NOW);
            booking.updateSegmentStatus(segment.id(), SegmentStatus.XX, NOW);
            booking.releasePayment(payment.id(), NOW);
            booking.releasePayment(payment.id(), NOW);
            booking.updateSegmentStatus(booking.segments().get(1).id(), SegmentStatus.XX, NOW);
            booking.fail("compensada", NOW);
            booking.fail("compensada otra vez", later);

            assertThat(booking.status()).isEqualTo(BookingStatus.FAILED);
            assertThat(booking.statusHistory()).filteredOn(c -> c.to() == BookingStatus.FAILED).hasSize(1);
        }

        @Test
        void a_cancelled_segment_cannot_be_confirmed_again() {
            Booking booking = heldBooking();
            Segment segment = booking.segments().getFirst();
            booking.updateSegmentStatus(segment.id(), SegmentStatus.XX, NOW);

            assertThatThrownBy(() -> booking.updateSegmentStatus(segment.id(), SegmentStatus.HK, NOW))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void a_captured_payment_cannot_be_released() {
            Booking booking = paymentAuthorizedBooking();
            booking.capturePayment(NOW);

            assertThatThrownBy(() -> booking.releasePayment(booking.payments().getFirst().id(), NOW))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void cancelling_is_idempotent() {
            Booking booking = heldBooking();
            booking.activeSegments().forEach(s -> booking.updateSegmentStatus(s.id(), SegmentStatus.XX, NOW));

            booking.cancel(null, NOW);
            booking.cancel("otra vez", later);

            assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(booking.statusHistory().getLast().reason()).isEqualTo("Cancelada");
        }
    }
}
