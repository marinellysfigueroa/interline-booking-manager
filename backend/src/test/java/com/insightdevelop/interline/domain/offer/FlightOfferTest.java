package com.insightdevelop.interline.domain.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airport.AirportCode;
import com.insightdevelop.interline.domain.booking.Fare;
import com.insightdevelop.interline.domain.booking.FlightNumber;
import com.insightdevelop.interline.domain.booking.PassengerCounts;
import com.insightdevelop.interline.domain.booking.SegmentStatus;
import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.DomainErrorCode;
import com.insightdevelop.interline.domain.shared.Miles;
import com.insightdevelop.interline.domain.shared.Money;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlightOfferTest {

    private static final OfferSegment BOG_MAD = segment("AV", "26", "BOG", "MAD", 20, 19);
    private static final OfferSegment MAD_FCO = segment("IB", "3234", "MAD", "FCO", 21, 13);

    @Test
    void exposes_operating_carriers_in_flight_order() {
        var offer = new FlightOffer("OF-1", AirlineCode.of("AV"),
                List.of(new Itinerary(ItineraryDirection.OUTBOUND, Duration.ofMinutes(850), List.of(BOG_MAD, MAD_FCO))),
                new Fare(Money.of("1849.60", "USD"), Miles.of(184_960)), Instant.parse("2026-11-01T15:30:00Z"));

        assertThat(offer.operatingCarriers()).extracting(AirlineCode::value).containsExactly("AV", "IB");
        assertThat(offer.isExpiredAt(Instant.parse("2026-11-01T15:30:00Z"))).isTrue();
        assertThat(offer.allSegments().getFirst().toRequestedSegment().status()).isEqualTo(SegmentStatus.UC);
    }

    @Test
    void rejects_non_contiguous_itineraries() {
        assertThatThrownBy(() -> new Itinerary(ItineraryDirection.OUTBOUND, Duration.ofHours(10),
                List.of(MAD_FCO, BOG_MAD))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void search_criteria_enforce_passenger_and_route_rules() {
        assertThatThrownBy(() -> new PassengerCounts(1, 0, 2))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("code").isEqualTo(DomainErrorCode.INFANT_RULE_VIOLATION);
        assertThatThrownBy(() -> new OfferSearchCriteria(AirportCode.of("BOG"), AirportCode.of("BOG"),
                LocalDate.of(2026, 11, 20), null, new PassengerCounts(1, 0, 0), null))
                .extracting("code").isEqualTo(DomainErrorCode.INVALID_ITINERARY);
        assertThatThrownBy(() -> new OfferSearchCriteria(AirportCode.of("LIM"), AirportCode.of("MIA"),
                LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 9), new PassengerCounts(1, 0, 0), null))
                .extracting("code").isEqualTo(DomainErrorCode.INVALID_ITINERARY);
    }

    private static OfferSegment segment(String carrier, String number, String from, String to, int day, int hour) {
        return new OfferSegment(AirlineCode.of(carrier), new FlightNumber(number), AirportCode.of(from),
                AirportCode.of(to), LocalDateTime.of(2026, 11, day, hour, 0), LocalDateTime.of(2026, 11, day, hour + 2, 0));
    }
}
