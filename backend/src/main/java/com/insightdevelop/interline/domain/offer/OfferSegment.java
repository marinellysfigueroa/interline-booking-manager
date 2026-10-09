package com.insightdevelop.interline.domain.offer;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airport.AirportCode;
import com.insightdevelop.interline.domain.booking.FlightNumber;
import com.insightdevelop.interline.domain.booking.Segment;
import java.time.LocalDateTime;
import java.util.Objects;

/** Tramo de una oferta (aún no reservado). Horas locales de cada aeropuerto. */
public record OfferSegment(
        AirlineCode operatingCarrier,
        FlightNumber flightNumber,
        AirportCode origin,
        AirportCode destination,
        LocalDateTime departure,
        LocalDateTime arrival) {

    public OfferSegment {
        Objects.requireNonNull(operatingCarrier, "operatingCarrier");
        Objects.requireNonNull(flightNumber, "flightNumber");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(departure, "departure");
        Objects.requireNonNull(arrival, "arrival");
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("Origen y destino no pueden ser iguales: " + origin);
        }
    }

    /** Convierte el tramo ofertado en un segmento solicitado ({@code UC}) de una reserva. */
    public Segment toRequestedSegment() {
        return Segment.requested(operatingCarrier, flightNumber, origin, destination, departure, arrival);
    }
}
