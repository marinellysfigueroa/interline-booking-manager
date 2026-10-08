package com.insightdevelop.interline.domain.offer;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airport.AirportCode;
import com.insightdevelop.interline.domain.booking.PassengerCounts;
import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.DomainErrorCode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Criterios de búsqueda de ofertas. Que la fecha no sea pasada lo valida la capa de
 * aplicación, que es quien tiene el reloj.
 *
 * @param returnDate        {@code null} para solo ida
 * @param validatingCarrier {@code null} para cualquier validadora
 */
public record OfferSearchCriteria(
        AirportCode origin,
        AirportCode destination,
        LocalDate departureDate,
        LocalDate returnDate,
        PassengerCounts passengers,
        AirlineCode validatingCarrier) {

    public OfferSearchCriteria {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(departureDate, "departureDate");
        Objects.requireNonNull(passengers, "passengers");
        if (origin.equals(destination)) {
            throw new BusinessRuleViolationException(DomainErrorCode.INVALID_ITINERARY,
                    "Origen y destino no pueden ser iguales: " + origin);
        }
        if (returnDate != null && returnDate.isBefore(departureDate)) {
            throw new BusinessRuleViolationException(DomainErrorCode.INVALID_ITINERARY,
                    "La fecha de regreso no puede ser anterior a la de salida");
        }
    }

    public Optional<LocalDate> returnOn() {
        return Optional.ofNullable(returnDate);
    }

    public Optional<AirlineCode> preferredValidatingCarrier() {
        return Optional.ofNullable(validatingCarrier);
    }

    public boolean isRoundTrip() {
        return returnDate != null;
    }
}
