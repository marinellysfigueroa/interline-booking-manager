package com.insightdevelop.interline.domain.booking;

import static com.insightdevelop.interline.domain.booking.BookingFixtures.DEPARTURE_DATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.DomainErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PassengerManifestTest {

    private static final LocalDate ADULT_DOB = LocalDate.of(1990, 1, 1);
    private static final LocalDate CHILD_DOB = LocalDate.of(2018, 6, 1);
    private static final LocalDate INFANT_DOB = LocalDate.of(2026, 1, 15);

    @Test
    void accepts_adult_child_and_infant_with_its_adult() {
        Passenger adult = Passenger.adult("Ana", "Pérez", ADULT_DOB);
        var manifest = PassengerManifest.of(List.of(
                adult,
                Passenger.child("Leo", "Pérez", CHILD_DOB),
                Passenger.infant("Sofía", "Pérez", INFANT_DOB, adult.id())), DEPARTURE_DATE);

        assertThat(manifest.counts()).isEqualTo(new PassengerCounts(1, 1, 1));
        assertThat(manifest.counts().seated()).isEqualTo(2);
    }

    @Test
    void rejects_more_infants_than_adults() {
        Passenger adult = Passenger.adult("Ana", "Pérez", ADULT_DOB);
        var passengers = List.of(adult,
                Passenger.infant("Sofía", "Pérez", INFANT_DOB, adult.id()),
                Passenger.infant("Mía", "Pérez", INFANT_DOB, adult.id()));

        assertRuleViolation(passengers, DomainErrorCode.INFANT_RULE_VIOLATION);
    }

    @Test
    void rejects_two_infants_on_the_same_adult_even_if_counts_match() {
        Passenger ana = Passenger.adult("Ana", "Pérez", ADULT_DOB);
        Passenger luis = Passenger.adult("Luis", "Gómez", ADULT_DOB);
        var passengers = List.of(ana, luis,
                Passenger.infant("Sofía", "Pérez", INFANT_DOB, ana.id()),
                Passenger.infant("Mía", "Pérez", INFANT_DOB, ana.id()));

        assertRuleViolation(passengers, DomainErrorCode.INFANT_RULE_VIOLATION);
    }

    @Test
    void rejects_infant_associated_with_a_child() {
        Passenger adult = Passenger.adult("Ana", "Pérez", ADULT_DOB);
        Passenger child = Passenger.child("Leo", "Pérez", CHILD_DOB);
        var passengers = List.of(adult, child, Passenger.infant("Sofía", "Pérez", INFANT_DOB, child.id()));

        assertRuleViolation(passengers, DomainErrorCode.INFANT_RULE_VIOLATION);
    }

    @Test
    void rejects_infant_associated_with_an_adult_outside_the_booking() {
        Passenger adult = Passenger.adult("Ana", "Pérez", ADULT_DOB);
        var passengers = List.of(adult, Passenger.infant("Sofía", "Pérez", INFANT_DOB, PassengerId.random()));

        assertRuleViolation(passengers, DomainErrorCode.INFANT_RULE_VIOLATION);
    }

    @Test
    void rejects_booking_without_adults() {
        assertRuleViolation(List.of(Passenger.child("Leo", "Pérez", CHILD_DOB)), DomainErrorCode.NO_ADULT_PASSENGER);
    }

    @Test
    void rejects_more_than_nine_seated_passengers() {
        var passengers = new ArrayList<Passenger>();
        for (int i = 0; i < 10; i++) {
            passengers.add(Passenger.adult("Pax" + i, "Test", ADULT_DOB));
        }
        assertRuleViolation(passengers, DomainErrorCode.TOO_MANY_PASSENGERS);
    }

    @Test
    void age_is_evaluated_on_the_first_departure_date() {
        // Cumple 2 años el 21-nov: el 20-nov sigue siendo INF, el 21-nov ya no.
        Passenger adult = Passenger.adult("Ana", "Pérez", ADULT_DOB);
        Passenger infant = Passenger.infant("Sofía", "Pérez", LocalDate.of(2024, 11, 21), adult.id());

        assertThat(PassengerManifest.of(List.of(adult, infant), DEPARTURE_DATE).size()).isEqualTo(2);
        assertThatThrownBy(() -> PassengerManifest.of(List.of(adult, infant), LocalDate.of(2026, 11, 21)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("code").isEqualTo(DomainErrorCode.PASSENGER_AGE_MISMATCH);
    }

    @Test
    void rejects_adult_type_for_an_eleven_year_old() {
        var passengers = List.of(Passenger.adult("Leo", "Pérez", LocalDate.of(2015, 1, 1)));

        assertRuleViolation(passengers, DomainErrorCode.PASSENGER_AGE_MISMATCH);
    }

    @Test
    void infant_type_requires_an_associated_adult() {
        assertThatThrownBy(() -> new Passenger(PassengerId.random(), PassengerType.INF, "Sofía", "Pérez",
                INFANT_DOB, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertRuleViolation(List<Passenger> passengers, DomainErrorCode expected) {
        assertThatThrownBy(() -> PassengerManifest.of(passengers, DEPARTURE_DATE))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("code").isEqualTo(expected);
    }
}
