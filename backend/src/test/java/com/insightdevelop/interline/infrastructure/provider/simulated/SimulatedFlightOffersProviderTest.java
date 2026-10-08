package com.insightdevelop.interline.infrastructure.provider.simulated;

import static org.assertj.core.api.Assertions.assertThat;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airport.AirportCode;
import com.insightdevelop.interline.domain.booking.PassengerCounts;
import com.insightdevelop.interline.domain.offer.FlightOffer;
import com.insightdevelop.interline.domain.offer.ItineraryDirection;
import com.insightdevelop.interline.domain.offer.OfferSearchCriteria;
import com.insightdevelop.interline.domain.shared.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proveedor offline ({@code app.flight-offers.provider=simulated}): prueba unitaria sin Quarkus. */
class SimulatedFlightOffersProviderTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);
    private final SimulatedFlightOffersProvider provider = new SimulatedFlightOffersProvider(
            Clock.fixed(Instant.parse("2026-11-01T15:00:00Z"), ZoneOffset.UTC));

    @Test
    void builds_connections_with_local_dates_and_prices_per_passenger() {
        List<FlightOffer> offers = provider.search(criteria("BOG", "FCO", null, new PassengerCounts(2, 0, 1)));

        assertThat(offers).singleElement().satisfies(offer -> {
            assertThat(offer.operatingCarriers()).extracting(AirlineCode::value).containsExactly("AV", "IB");
            assertThat(offer.allSegments().get(1).departure()).isEqualTo(DATE.plusDays(1).atTime(13, 15));
            assertThat(offer.fare().total()).isEqualTo(Money.of("2352.00", "USD"));
            assertThat(offer.expiresAt()).isEqualTo(Instant.parse("2026-11-01T15:30:00Z"));
        });
    }

    @Test
    void round_trip_combines_routes_of_the_same_validating_carrier() {
        List<FlightOffer> offers = provider.search(criteria("LIM", "MIA", DATE.plusDays(7), new PassengerCounts(1, 0, 0)));

        assertThat(offers).singleElement().satisfies(offer -> assertThat(offer.itineraries())
                .extracting(i -> i.direction()).containsExactly(ItineraryDirection.OUTBOUND, ItineraryDirection.INBOUND));
    }

    @Test
    void unknown_route_returns_no_offers() {
        assertThat(provider.search(criteria("SCL", "JFK", null, new PassengerCounts(1, 0, 0)))).isEmpty();
    }

    private static OfferSearchCriteria criteria(String from, String to, LocalDate back, PassengerCounts pax) {
        return new OfferSearchCriteria(AirportCode.of(from), AirportCode.of(to), DATE, back, pax, null);
    }
}
