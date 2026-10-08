package com.insightdevelop.interline.domain.offer;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.booking.Fare;
import com.insightdevelop.interline.domain.booking.PassengerCounts;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Oferta normalizada devuelta por un {@code FlightOffersProvider}, independiente del
 * formato del proveedor (estilo Amadeus u otro).
 *
 * @param passengers composición para la que se tarificó la oferta: la reserva debe
 *                   tener exactamente esos pasajeros para respetar el precio
 */
public record FlightOffer(
        String offerId,
        AirlineCode validatingCarrier,
        List<Itinerary> itineraries,
        Fare fare,
        PassengerCounts passengers,
        Instant expiresAt) {

    public FlightOffer {
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(validatingCarrier, "validatingCarrier");
        Objects.requireNonNull(fare, "fare");
        Objects.requireNonNull(passengers, "passengers");
        Objects.requireNonNull(expiresAt, "expiresAt");
        itineraries = List.copyOf(itineraries);
        if (itineraries.isEmpty() || itineraries.size() > 2) {
            throw new IllegalArgumentException("Una oferta tiene 1 (ida) o 2 (ida y vuelta) itinerarios");
        }
    }

    public List<OfferSegment> allSegments() {
        return itineraries.stream().flatMap(i -> i.segments().stream()).toList();
    }

    /** Operadoras sin repetir, en orden de vuelo. */
    public Set<AirlineCode> operatingCarriers() {
        return allSegments().stream().map(OfferSegment::operatingCarrier)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
