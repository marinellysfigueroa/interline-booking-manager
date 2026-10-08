package com.insightdevelop.interline.infrastructure.provider.amadeus;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airport.AirportCode;
import com.insightdevelop.interline.domain.booking.Fare;
import com.insightdevelop.interline.domain.booking.FlightNumber;
import com.insightdevelop.interline.domain.offer.FlightOffer;
import com.insightdevelop.interline.domain.offer.Itinerary;
import com.insightdevelop.interline.domain.offer.ItineraryDirection;
import com.insightdevelop.interline.domain.offer.OfferSearchCriteria;
import com.insightdevelop.interline.domain.offer.OfferSegment;
import com.insightdevelop.interline.domain.shared.Miles;
import com.insightdevelop.interline.domain.shared.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Traduce el contrato estilo Amadeus al modelo de dominio (capa anticorrupción). Una oferta
 * que no se puede interpretar se descarta con un aviso en lugar de romper toda la búsqueda.
 */
final class AmadeusOfferMapper {

    private static final Logger LOG = Logger.getLogger(AmadeusOfferMapper.class);

    private AmadeusOfferMapper() {
    }

    static List<FlightOffer> toDomain(AmadeusFlightOffersResponse response, OfferSearchCriteria criteria,
            AmadeusStyleConfig config, Instant now) {
        if (response == null || response.data() == null) {
            return List.of();
        }
        List<FlightOffer> offers = new ArrayList<>();
        for (AmadeusFlightOffersResponse.Offer offer : response.data()) {
            try {
                offers.add(toDomain(offer, criteria, config, now));
            } catch (RuntimeException e) {
                LOG.warnf("Oferta %s del proveedor descartada: %s", offer.id(), e.getMessage());
            }
        }
        return offers;
    }

    private static FlightOffer toDomain(AmadeusFlightOffersResponse.Offer offer, OfferSearchCriteria criteria,
            AmadeusStyleConfig config, Instant now) {
        List<Itinerary> itineraries = new ArrayList<>();
        for (int i = 0; i < offer.itineraries().size(); i++) {
            var itinerary = offer.itineraries().get(i);
            itineraries.add(new Itinerary(i == 0 ? ItineraryDirection.OUTBOUND : ItineraryDirection.INBOUND,
                    Duration.parse(itinerary.duration()),
                    itinerary.segments().stream().map(AmadeusOfferMapper::toDomain).toList()));
        }
        // Los ids del proveedor solo son únicos dentro de una búsqueda: generamos uno propio.
        String offerId = "AMS-" + UUID.randomUUID().toString().substring(0, 8);
        AirlineCode validating = AirlineCode.of(offer.validatingAirlineCodes().getFirst());
        return new FlightOffer(offerId, validating, itineraries, fare(offer.price(), config), criteria.passengers(),
                now.plus(config.offerTtl()));
    }

    /**
     * Simplificación: el dominio guarda la aerolínea <b>operadora</b> (la que cuenta para la regla
     * interline) con el número de vuelo comercial; los códigos compartidos quedan fuera de alcance.
     */
    private static OfferSegment toDomain(AmadeusFlightOffersResponse.Segment segment) {
        String operating = Optional.ofNullable(segment.operating())
                .map(AmadeusFlightOffersResponse.Operating::carrierCode)
                .orElse(segment.carrierCode());
        return new OfferSegment(AirlineCode.of(operating), new FlightNumber(segment.number()),
                AirportCode.of(segment.departure().iataCode()), AirportCode.of(segment.arrival().iataCode()),
                LocalDateTime.parse(segment.departure().at()), LocalDateTime.parse(segment.arrival().at()));
    }

    private static Fare fare(AmadeusFlightOffersResponse.Price price, AmadeusStyleConfig config) {
        Currency currency = Currency.getInstance(price.currency());
        String amount = price.grandTotal() != null ? price.grandTotal() : price.total();
        // Datos de un tercero: se redondea a la escala de la moneda en vez de rechazar la oferta.
        BigDecimal total = new BigDecimal(amount).setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_UP);
        long miles = total.multiply(config.milesPerCurrencyUnit()).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new Fare(new Money(total, currency), Miles.of(miles));
    }
}
