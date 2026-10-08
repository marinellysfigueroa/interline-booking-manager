package com.insightdevelop.interline.infrastructure.persistence;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airport.AirportCode;
import com.insightdevelop.interline.domain.booking.Fare;
import com.insightdevelop.interline.domain.booking.FlightNumber;
import com.insightdevelop.interline.domain.booking.PassengerCounts;
import com.insightdevelop.interline.domain.offer.FlightOffer;
import com.insightdevelop.interline.domain.offer.Itinerary;
import com.insightdevelop.interline.domain.offer.ItineraryDirection;
import com.insightdevelop.interline.domain.offer.OfferSegment;
import com.insightdevelop.interline.domain.shared.Miles;
import com.insightdevelop.interline.domain.shared.Money;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Registro para reflexión en el ejecutable nativo de los records de dominio que
 * {@link OfferStoreAdapter} serializa a JSONB con Jackson. Quarkus no puede deducirlos (no
 * aparecen en ninguna firma JAX-RS) y el dominio no se anota.
 *
 * <p>Spring Native/AOT: el equivalente sería un {@code RuntimeHintsRegistrar}.
 */
@RegisterForReflection(targets = {
        FlightOffer.class, Itinerary.class, OfferSegment.class, ItineraryDirection.class, Fare.class,
        PassengerCounts.class, Money.class, Miles.class, AirlineCode.class, AirportCode.class, FlightNumber.class
})
public final class PersistenceNativeReflectionConfig {

    private PersistenceNativeReflectionConfig() {
    }
}
