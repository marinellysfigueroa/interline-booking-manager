package com.insightdevelop.interline.application.offer;

import com.insightdevelop.interline.domain.airline.InterlineEligibility;
import com.insightdevelop.interline.domain.offer.FlightOffer;

/** Oferta más su evaluación interline (si puede emitirse en un solo ticket). */
public record OfferSearchResult(FlightOffer offer, InterlineEligibility interline) {
}
