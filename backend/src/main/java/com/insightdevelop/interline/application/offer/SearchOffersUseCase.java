package com.insightdevelop.interline.application.offer;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airline.InterlineEligibility;
import com.insightdevelop.interline.domain.airline.InterlineTicketingPolicy;
import com.insightdevelop.interline.domain.offer.FlightOffer;
import com.insightdevelop.interline.domain.offer.OfferSearchCriteria;
import com.insightdevelop.interline.domain.port.AirlineRepository;
import com.insightdevelop.interline.domain.port.FlightOffersProvider;
import com.insightdevelop.interline.domain.port.OfferStore;
import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.DomainErrorCode;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Busca ofertas en el proveedor configurado, las guarda para poder reservarlas por
 * {@code offerId} y evalúa la regla interline de cada una.
 */
@ApplicationScoped
public class SearchOffersUseCase {

    private final FlightOffersProvider provider;
    private final OfferStore offerStore;
    private final AirlineRepository airlines;
    private final InterlineTicketingPolicy policy;
    private final Clock clock;

    SearchOffersUseCase(FlightOffersProvider provider, OfferStore offerStore, AirlineRepository airlines,
            InterlineTicketingPolicy policy, Clock clock) {
        this.provider = provider;
        this.offerStore = offerStore;
        this.airlines = airlines;
        this.policy = policy;
        this.clock = clock;
    }

    public List<OfferSearchResult> search(OfferSearchCriteria criteria) {
        if (criteria.departureDate().isBefore(LocalDate.now(clock))) {
            throw new BusinessRuleViolationException(DomainErrorCode.INVALID_ITINERARY,
                    "La fecha de salida %s ya pasó".formatted(criteria.departureDate()));
        }
        // Un portal white-label solo vende lo que valida su aerolínea (validadora preferida o X-Tenant).
        List<FlightOffer> offers = provider.search(criteria).stream()
                .filter(o -> criteria.preferredValidatingCarrier().map(o.validatingCarrier()::equals).orElse(true))
                .toList();
        offerStore.saveAll(offers);
        return offers.stream().map(offer -> new OfferSearchResult(offer, evaluate(offer))).toList();
    }

    private InterlineEligibility evaluate(FlightOffer offer) {
        return airlines.findByCode(offer.validatingCarrier())
                .map(airline -> policy.evaluate(airline, offer.operatingCarriers()))
                .orElseGet(() -> unknownValidatingCarrier(offer.validatingCarrier(), offer.operatingCarriers()));
    }

    /** Validadora fuera del catálogo: no se le conocen acuerdos, solo puede emitir sus propios vuelos. */
    private static InterlineEligibility unknownValidatingCarrier(AirlineCode validating, Set<AirlineCode> operating) {
        Set<AirlineCode> missing = new LinkedHashSet<>(operating);
        missing.remove(validating);
        return new InterlineEligibility(validating, operating, missing);
    }
}
