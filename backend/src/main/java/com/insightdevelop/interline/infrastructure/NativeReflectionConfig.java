package com.insightdevelop.interline.infrastructure;

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
import com.insightdevelop.interline.infrastructure.rest.dto.BookingActionDto;
import com.insightdevelop.interline.infrastructure.rest.dto.BookingStatusDto;
import com.insightdevelop.interline.infrastructure.rest.dto.PassengerTypeDto;
import com.insightdevelop.interline.infrastructure.rest.dto.PaymentMethodDto;
import com.insightdevelop.interline.infrastructure.rest.dto.PaymentStatusDto;
import com.insightdevelop.interline.infrastructure.rest.dto.SegmentStatusDto;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Registro para reflexión en el ejecutable nativo de lo que Quarkus no puede deducir solo:
 * <ul>
 *   <li>los records de dominio que Jackson serializa a JSONB ({@code OfferStoreAdapter});</li>
 *   <li>los enums de los DTO generados: los recursos devuelven {@code Response}, así que sus
 *       tipos no aparecen en ninguna firma JAX-RS. Las clases DTO ya llevan
 *       {@code @RegisterForReflection} desde openapi-generator, pero el generador no anota los
 *       enums. {@code NativeReflectionCoverageTest} falla si alguno queda fuera.</li>
 * </ul>
 *
 * <p>Vive en infraestructura para no anotar el dominio. En Spring Native/AOT el equivalente
 * sería un {@code RuntimeHintsRegistrar}.
 */
@RegisterForReflection(targets = {
        FlightOffer.class, Itinerary.class, OfferSegment.class, ItineraryDirection.class, Fare.class,
        PassengerCounts.class, Money.class, Miles.class, AirlineCode.class, AirportCode.class, FlightNumber.class,
        // enums de los DTO generados desde el contrato
        BookingActionDto.class, BookingStatusDto.class, PassengerTypeDto.class, PaymentMethodDto.class,
        PaymentStatusDto.class, SegmentStatusDto.class
})
public final class NativeReflectionConfig {

    private NativeReflectionConfig() {
    }
}
