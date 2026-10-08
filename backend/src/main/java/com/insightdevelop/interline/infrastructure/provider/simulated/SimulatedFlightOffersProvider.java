package com.insightdevelop.interline.infrastructure.provider.simulated;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airport.AirportCode;
import com.insightdevelop.interline.domain.booking.Fare;
import com.insightdevelop.interline.domain.booking.FlightNumber;
import com.insightdevelop.interline.domain.booking.PassengerCounts;
import com.insightdevelop.interline.domain.offer.FlightOffer;
import com.insightdevelop.interline.domain.offer.Itinerary;
import com.insightdevelop.interline.domain.offer.ItineraryDirection;
import com.insightdevelop.interline.domain.offer.OfferSearchCriteria;
import com.insightdevelop.interline.domain.offer.OfferSegment;
import com.insightdevelop.interline.domain.port.FlightOffersProvider;
import com.insightdevelop.interline.domain.shared.Miles;
import com.insightdevelop.interline.domain.shared.Money;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Proveedor de ofertas <b>offline</b> con un horario fijo de demostración (vuelos y
 * tarifas ficticios). Sirve para correr la aplicación sin WireMock ni red. El adaptador
 * estilo Amadeus (fase 3) es el que consume un contrato HTTP.
 *
 * <p>Precio: tarifa base × (adultos + niños) + 10 % por infante; 100 millas por USD.
 */
@ApplicationScoped
@Identifier("simulated")
public class SimulatedFlightOffersProvider implements FlightOffersProvider {

    static final Duration OFFER_TTL = Duration.ofMinutes(30);

    /** Tramo con desfases en días respecto a la fecha del itinerario (horas locales). */
    private record Leg(String carrier, String number, String from, String to, int departureDay, LocalTime departure,
            int arrivalDay, LocalTime arrival) {
    }

    private record Route(String validating, String baseFareUsd, Duration duration, List<Leg> legs) {
    }

    private static final Map<String, List<Route>> SCHEDULE = Map.of(
            "BOG-MAD", List.of(new Route("AV", "980.00", Duration.parse("PT10H35M"), List.of(
                    new Leg("AV", "26", "BOG", "MAD", 0, LocalTime.of(19, 5), 1, LocalTime.of(11, 40))))),
            "MAD-FCO", List.of(new Route("IB", "160.00", Duration.parse("PT2H30M"), List.of(
                    new Leg("IB", "3234", "MAD", "FCO", 0, LocalTime.of(13, 15), 0, LocalTime.of(15, 45))))),
            "BOG-FCO", List.of(new Route("AV", "1120.00", Duration.parse("PT14H40M"), List.of(
                    new Leg("AV", "26", "BOG", "MAD", 0, LocalTime.of(19, 5), 1, LocalTime.of(11, 40)),
                    new Leg("IB", "3234", "MAD", "FCO", 1, LocalTime.of(13, 15), 1, LocalTime.of(15, 45))))),
            "FCO-BOG", List.of(new Route("AV", "1095.00", Duration.parse("PT17H5M"), List.of(
                    new Leg("IB", "3231", "FCO", "MAD", 0, LocalTime.of(7, 0), 0, LocalTime.of(9, 35)),
                    new Leg("AV", "27", "MAD", "BOG", 0, LocalTime.of(12, 5), 0, LocalTime.of(16, 5))))),
            "LIM-MIA", List.of(new Route("LA", "540.00", Duration.parse("PT5H50M"), List.of(
                    new Leg("LA", "2470", "LIM", "MIA", 0, LocalTime.of(9, 10), 0, LocalTime.of(15, 0))))),
            "MIA-LIM", List.of(new Route("LA", "560.00", Duration.parse("PT5H40M"), List.of(
                    new Leg("LA", "2471", "MIA", "LIM", 0, LocalTime.of(17, 0), 0, LocalTime.of(22, 40))))),
            // Validada por LA con un tramo de AV: LA no tiene acuerdo con AV → no elegible para ticket único.
            "LIM-MAD", List.of(new Route("LA", "1210.00", Duration.parse("PT16H55M"), List.of(
                    new Leg("LA", "2486", "LIM", "BOG", 0, LocalTime.of(11, 0), 0, LocalTime.of(14, 10)),
                    new Leg("AV", "26", "BOG", "MAD", 0, LocalTime.of(19, 5), 1, LocalTime.of(11, 40))))));

    private final Clock clock;

    SimulatedFlightOffersProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public List<FlightOffer> search(OfferSearchCriteria criteria) {
        List<FlightOffer> offers = new ArrayList<>();
        for (Route out : routes(criteria.origin(), criteria.destination())) {
            Itinerary outbound = itinerary(ItineraryDirection.OUTBOUND, out, criteria.departureDate());
            if (criteria.returnOn().isEmpty()) {
                offers.add(offer(criteria, out.validating(), List.of(outbound), fare(criteria, out, null)));
                continue;
            }
            for (Route back : routes(criteria.destination(), criteria.origin())) {
                if (back.validating().equals(out.validating())) {
                    Itinerary inbound = itinerary(ItineraryDirection.INBOUND, back, criteria.returnOn().get());
                    offers.add(offer(criteria, out.validating(), List.of(outbound, inbound), fare(criteria, out, back)));
                }
            }
        }
        return offers;
    }

    private static List<Route> routes(AirportCode from, AirportCode to) {
        return Optional.ofNullable(SCHEDULE.get(from.value() + "-" + to.value())).orElse(List.of());
    }

    private FlightOffer offer(OfferSearchCriteria criteria, String validating, List<Itinerary> itineraries, Fare fare) {
        return new FlightOffer("SIM-" + UUID.randomUUID().toString().substring(0, 8), AirlineCode.of(validating),
                itineraries, fare, criteria.passengers(), clock.instant().plus(OFFER_TTL));
    }

    private static Itinerary itinerary(ItineraryDirection direction, Route route, LocalDate date) {
        List<OfferSegment> segments = route.legs().stream().map(leg -> new OfferSegment(
                AirlineCode.of(leg.carrier()), new FlightNumber(leg.number()),
                AirportCode.of(leg.from()), AirportCode.of(leg.to()),
                date.plusDays(leg.departureDay()).atTime(leg.departure()),
                date.plusDays(leg.arrivalDay()).atTime(leg.arrival()))).toList();
        return new Itinerary(direction, route.duration(), segments);
    }

    private static Fare fare(OfferSearchCriteria criteria, Route out, Route back) {
        PassengerCounts pax = criteria.passengers();
        BigDecimal base = new BigDecimal(out.baseFareUsd());
        if (back != null) {
            base = base.add(new BigDecimal(back.baseFareUsd()));
        }
        BigDecimal total = base.multiply(BigDecimal.valueOf(pax.seated()))
                .add(base.multiply(new BigDecimal("0.10")).multiply(BigDecimal.valueOf(pax.infants())))
                .setScale(2, RoundingMode.HALF_UP);
        return new Fare(Money.of(total, "USD"), Miles.of(total.movePointRight(2).longValueExact()));
    }
}
